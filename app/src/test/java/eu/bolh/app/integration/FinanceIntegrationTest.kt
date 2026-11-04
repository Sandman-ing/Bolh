package eu.bolh.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.model.AccountType
import eu.bolh.app.domain.model.TransactionType
import eu.bolh.app.ui.stats.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Деньги на настоящей базе: удаление счёта, разовые платежи, переводы.
 *
 * Каждый тест здесь закрепляет поведение, которое тестировщики однажды уже
 * нашли сломанным, — чтобы оно не сломалось снова незаметно.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FinanceIntegrationTest {

    private lateinit var app: TestApp

    @Before
    fun setUp() {
        // ViewModel считает на главном потоке, а он занят runBlocking.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = TestApp()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun statsStopCountingExpensesOfDeletedAccount() = runBlocking {
        val f = app.finance
        val live = f.createAccount("Live", "credit_card", AccountType.Card, 0L)
        val doomed = f.createAccount("Doomed", "credit_card", AccountType.Card, 0L)
        f.record(app.expense(live, 1_000L))
        f.record(app.expense(doomed, 5_000L))

        val stats = StatsViewModel(app.habits, app.goals, f, app.container.settings)
        val before = withTimeout(10_000) { stats.state.first { it.loaded && it.spendMinor > 0L } }
        assertEquals(6_000L, before.spendMinor)

        f.deleteAccount(doomed)
        val after = withTimeout(10_000) { stats.state.first { it.loaded && it.spendMinor != 6_000L } }
        assertEquals(1_000L, after.spendMinor)
    }

    @Test
    fun transferFromDeletedAccountKeepsBalanceOfLiveAccount() = runBlocking {
        val f = app.finance
        val live = f.createAccount("Live", "credit_card", AccountType.Card, 0L)
        val doomed = f.createAccount("Doomed", "credit_card", AccountType.Card, 0L)
        f.record(app.transfer(from = doomed, to = live, amountMinor = 2_000L))

        f.deleteAccount(doomed)

        val account = f.observeAccounts().first().single()
        val transactions = f.observeTransactions().first()
        assertEquals(live, account.id)
        assertEquals(2_000L, FinanceAnalytics.balanceOf(account, transactions))
        assertTrue(transactions.all { it.type == TransactionType.Transfer })
    }

    @Test
    fun deletingAccountRemovesItsRecurringPayments() = runBlocking {
        val f = app.finance
        val live = f.createAccount("Live", "credit_card", AccountType.Card, 0L)
        val doomed = f.createAccount("Doomed", "credit_card", AccountType.Card, 0L)
        val keep = app.payment(live, "Keep").also { f.createRecurring(it) }
        val gone = app.payment(doomed, "Gone").also { f.createRecurring(it) }

        f.deleteAccount(doomed)

        assertEquals(listOf(keep.id), f.observeRecurring().first().map { it.id })
        assertEquals(listOf(keep.id), f.allRecurring().map { it.id })
        assertNull(f.findRecurring(gone.id))
        // «Записать» у платежа удалённого счёта ничего не заводит.
        f.recordRecurring(gone.id)
        assertTrue(f.observeTransactions().first().isEmpty())
    }

    @Test
    fun paymentOfAccountDeletedBeforeThisVersionIsHidden() = runBlocking {
        val f = app.finance
        val old = f.createAccount("Old", "credit_card", AccountType.Card, 0L)
        val orphan = app.payment(old, "Orphan").also { f.createRecurring(it) }
        // Так удалялся счёт до 0.4.1: платёж оставался в базе живым.
        app.container.database.financeDao().softDeleteAccount(old, 1L)

        assertTrue(f.observeRecurring().first().isEmpty())
        assertTrue(f.allRecurring().isEmpty())
        assertNull(f.findRecurring(orphan.id))
    }

    @Test
    fun oneTimePaymentIsRecordedOnceAndLeavesTheList() = runBlocking {
        val f = app.finance
        val card = f.createAccount("Card", "credit_card", AccountType.Card, 0L)
        val regular = app.payment(card, "Server").also { f.createRecurring(it) }
        val once = app.payment(card, "Claude", once = true).also { f.createRecurring(it) }

        // Второе нажатие и «Пропустить» после записи не должны ничего менять.
        f.recordRecurring(once.id)
        f.recordRecurring(once.id)
        f.skipRecurring(once.id)

        val recorded = f.observeTransactions().first().filter { it.recurringId == once.id }
        assertEquals(1, recorded.size)
        assertEquals(listOf(regular.id), f.observeRecurring().first().map { it.id })
        val stored = f.findRecurring(once.id)!!
        assertFalse(stored.isActive)
        assertTrue(stored.recurrence.once)
    }

    @Test
    fun regularPaymentMovesToNextPeriodAfterRecord() = runBlocking {
        val f = app.finance
        val card = f.createAccount("Card", "credit_card", AccountType.Card, 0L)
        val regular = app.payment(card, "Server").also { f.createRecurring(it) }

        f.recordRecurring(regular.id)

        val stored = f.findRecurring(regular.id)!!
        assertTrue(stored.isActive)
        assertEquals(regular.nextDate.plusMonths(1), stored.nextDate)
        assertEquals(1, f.observeTransactions().first().size)
    }
}
