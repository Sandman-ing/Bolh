package eu.bolh.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.bolh.app.data.backup.NotBolhBackupException
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.data.prefs.LanguagePrefs
import eu.bolh.app.data.prefs.ThemePrefs
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.model.AccountType
import eu.bolh.app.domain.model.EntryLevel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Резервная копия на настоящей базе. Данные живут только на телефоне, и копия —
 * единственный способ их не потерять: всё, что в неё ушло, должно вернуться,
 * а негодный файл не должен тронуть то, что уже есть.
 */
@RunWith(AndroidJUnit4::class)
class BackupIntegrationTest {

    private lateinit var app: TestApp
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val backup get() = app.container.backupService

    @Before
    fun setUp() {
        app = TestApp()
    }

    private suspend fun export(): ByteArray =
        ByteArrayOutputStream().also { backup.exportJson(it) }.toByteArray()

    private suspend fun goalNames(): List<String> = app.goals.observeAllGoals().first().map { it.name }

    @Test
    fun backupBringsEverythingBackAfterWipe() = runBlocking {
        val goal = app.goal("Laptop")
        val stage = app.goals.addStage(goal, "Collect papers")
        app.goals.addTask(stage, "Translate documents")
        val jar = app.finance.createJar(goal, 100_000L)
        app.finance.addContribution(jar, 30_000L)
        val habit = app.habit("Read")
        app.habits.markDay(habit, app.finance.today(), EntryLevel.Norm)
        val card = app.finance.createAccount("Card", "credit_card", AccountType.Card, 5_000L)
        app.finance.record(app.expense(card, 1_200L).copy(note = "Coffee"))
        val rent = app.payment(card, "Rent").also { app.finance.createRecurring(it) }
        ThemePrefs.set(context, ThemePrefs.LIGHT)
        LanguagePrefs.set(context, "ru")
        CurrencyPrefs.set(context, "AMD")

        val file = ByteArrayOutputStream()
        val exported = backup.exportJson(file)
        assertTrue("backup holds only $exported rows", exported >= 8)

        // Новый телефон: ни данных, ни настроек.
        withContext(Dispatchers.IO) { app.container.database.clearAllTables() }
        ThemePrefs.set(context, ThemePrefs.DARK)
        LanguagePrefs.set(context, "en")
        CurrencyPrefs.set(context, "EUR")
        assertTrue(goalNames().isEmpty())

        val result = backup.importJson(ByteArrayInputStream(file.toByteArray()))
        assertEquals(exported, result.rows)
        assertEquals("ru", result.language)

        assertEquals(listOf("Laptop"), goalNames())
        assertEquals(listOf("Collect papers"), app.goals.observeStagesForGoal(goal).first().map { it.name })
        assertEquals(listOf("Translate documents"), app.goals.observeTasks().first()[stage].orEmpty().map { it.name })
        assertEquals(listOf(30_000L), app.finance.observeContributions().first()[jar].orEmpty().map { it.amountMinor })
        assertTrue("habit mark is lost", app.habits.isMarked(habit, app.finance.today()))
        val accounts = app.finance.observeAccounts().first()
        val transactions = app.finance.observeTransactions().first()
        assertEquals(listOf("Card"), accounts.map { it.name })
        assertEquals(listOf("Coffee"), transactions.map { it.note })
        assertEquals(3_800L, FinanceAnalytics.balanceOf(accounts.single(), transactions))
        assertEquals(listOf(rent.id), app.finance.observeRecurring().first().map { it.id })
        assertEquals(ThemePrefs.LIGHT, ThemePrefs.get(context))
        assertEquals("ru", LanguagePrefs.get(context))
        assertEquals("AMD", CurrencyPrefs.get(context))
    }

    @Test
    fun restoreReplacesDataInsteadOfMixing() = runBlocking {
        app.goal("From backup")
        val file = export()
        app.goal("Made after backup")

        backup.importJson(ByteArrayInputStream(file))

        assertEquals(listOf("From backup"), goalNames())
    }

    @Test
    fun foreignFileLeavesDataUntouched() = runBlocking {
        app.goal("Keep me")
        try {
            backup.importJson(ByteArrayInputStream("""{"hello": 1}""".toByteArray()))
            fail("a foreign file was accepted as a backup")
        } catch (expected: NotBolhBackupException) {
        }
        assertEquals(listOf("Keep me"), goalNames())
    }

    @Test
    fun cutOffBackupLeavesDataUntouched() = runBlocking {
        app.goal("Keep me")
        val file = export()
        try {
            backup.importJson(ByteArrayInputStream(file.copyOf(file.size / 2)))
            fail("a cut-off backup was accepted")
        } catch (expected: NotBolhBackupException) {
        }
        assertEquals(listOf("Keep me"), goalNames())
    }
}
