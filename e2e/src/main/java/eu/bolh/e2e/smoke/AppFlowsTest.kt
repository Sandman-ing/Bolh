package eu.bolh.e2e.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import eu.bolh.e2e.BolhRule
import eu.bolh.e2e.BolhUi
import eu.bolh.e2e.Flows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Главные сценарии на настоящей release-сборке, через экран, как у человека.
 * Каждый тест начинается с чистой установки.
 *
 * Release-сборка ужата R8: класс, который нужен только через отражение,
 * мог быть вырезан, и это видно лишь при запуске. Поэтому здесь проходится
 * каждый раздел, а падение приложения валит тест, даже если экран успел
 * показать нужное.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowsTest {

    @get:Rule
    val bolh = BolhRule()

    private val ui get() = bolh.ui
    private val flows by lazy { Flows(ui) }

    @Test
    fun everyTabOpensOnFreshInstall() {
        ui.expectText("Create your first goal")
        ui.tab("Habits")
        ui.expectText("Create your first habit")
        ui.tab("Finance")
        ui.expectText("Add your first account")
        ui.tab("Stats")
        ui.expectText("Nothing to count yet")
        ui.tapDesc("Settings")
        ui.expectText("Language")
        ui.back()
        ui.expectText("Nothing to count yet")
    }

    @Test
    fun goalGetsBudgetStagesAndFullWidthTask() {
        flows.createGoal("Laptop")
        ui.tap("Laptop")

        ui.tap("Add a budget")
        ui.fill("How much is needed", "250")
        ui.tap("Start")
        ui.expectTextMatches(".*0 of .*250.*")

        ui.fill("Stage name", "Collect papers")
        ui.tap("Add a stage")
        ui.expectShownText("Collect papers")
        ui.fill("Stage name", "Visit the town hall")
        ui.tap("Add a stage")
        ui.expectShownText("Visit the town hall")

        // Поле задачи второго этапа раньше сжималось кнопкой до столбика.
        ui.hideKeyboard()
        val stage = ui.text("Visit the town hall")
        val addTask = ui.allTexts("Add a task").firstOrNull { it.visibleBounds.top > stage.visibleBounds.top }
            ?: run { fail("No \"Add a task\" under the second stage"); error("unreachable") }
        ui.click(addTask)
        val field = ui.focusedField()
        val share = field.visibleBounds.width().toFloat() / ui.device.displayWidth
        assertTrue("task field takes only ${(share * 100).toInt()}% of the screen width", share > 0.6f)

        field.text = "Translate every document"
        ui.hideKeyboard(expectIt = true)
        val typed = By.clazz(BolhUi.EDIT_TEXT).text("Translate every document")
        val submit = ui.textBelow("Add a task") {
            ui.find(typed) ?: run { fail("Typed task is not in its field"); error("unreachable") }
        }
        ui.click(submit)
        ui.expectShownText("Translate every document")

        // Карточка цели показывает бюджет, а плашки «без привычек» больше нет.
        ui.back()
        ui.expectText("Laptop")
        ui.expectTextMatches(".*0 of .*250.*")
        ui.expectNoTextContains("no habits")
    }

    @Test
    fun habitIsCreatedAndMarked() {
        flows.createHabit("Water the plants")
        flows.markHabit("Water the plants")
        // Отметка переживает уход с вкладки.
        ui.tab("Goals")
        ui.tab("Habits")
        ui.expectDesc("Mark done")
    }

    @Test
    fun expenseNoteStaysAboveKeyboard() {
        flows.createAccount("Main card")
        ui.tapDesc("Create")
        ui.fill("Amount", "12")
        ui.tap("Date, note, goal")

        ui.field("Note").click()
        val keyboard = ui.awaitKeyboard()
        ui.device.waitForIdle()
        val note = ui.focusedField()
        val noteBottom = note.visibleBounds.bottom
        assertTrue(
            "note field ends at $noteBottom px, under the keyboard that starts at ${keyboard.top} px",
            noteBottom <= keyboard.top,
        )

        note.text = "Coffee with Anna"
        ui.tap("Record")
        ui.expectNoText("Date, note, goal", BolhUi.TIMEOUT)
        ui.tap("All transactions")
        ui.expectText("Coffee with Anna")
    }

    @Test
    fun oneTimePaymentIsRecordedOnceAndLeavesTheList() {
        flows.createAccount("Main card")
        flows.addPayment("Claude", "37", once = true)
        ui.expectTextContains("one-time")

        ui.tap("Record")
        ui.expectText("No payments yet")
        ui.expectNoText("Claude")

        ui.tab("Stats")
        ui.expectTextContains("37")
    }

    @Test
    fun deletedAccountLeavesStats() {
        flows.createAccount("Live card")
        flows.createAccount("Old card")
        flows.recordExpense("Live card", "61")
        flows.recordExpense("Old card", "700")

        ui.tab("Stats")
        ui.expectTextContains("761")

        ui.tab("Finance")
        ui.longTap("Old card")
        ui.expectTextContains("Delete")
        ui.tap("Delete")
        ui.expectNoText("Old card")
        ui.expectText("Live card")

        ui.tab("Stats")
        ui.expectTextContains("61")
        ui.expectNoTextContains("761")
    }

    @Test
    fun languageSwitchesToRussianAndBack() {
        flows.openSettings()
        ui.tap("English")
        ui.tap("Русский")
        ui.expectText("Язык")
        ui.tap("Русский")
        ui.tap("English")
        ui.expectText("Language")
        ui.back()
        ui.expectText("Nothing to count yet")
        ui.expectTab("Goals")
    }

    @Test
    fun themeSwitchRepaintsScreen() {
        flows.openSettings()
        // По умолчанию тема тёмная.
        assertTrue("dark theme should be dark", ui.lightShare() < 0.4f)
        ui.tap("Light")
        assertTrue("light theme should be light", ui.lightShare() > 0.6f)
        ui.back()
        assertTrue("other screens should follow the light theme", ui.lightShare() > 0.6f)

        flows.openSettings()
        ui.tap("Dark")
        assertTrue("dark theme should be dark again", ui.lightShare() < 0.4f)
        assertTrue(ui.find(By.text("Language")) != null)
    }
}
