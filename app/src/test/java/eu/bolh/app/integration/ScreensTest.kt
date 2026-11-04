package eu.bolh.app.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.bolh.app.domain.model.AccountType
import eu.bolh.app.ui.finance.RecurringEditorScreen
import eu.bolh.app.ui.finance.RecurringEditorViewModel
import eu.bolh.app.ui.finance.TransactionEditorViewModel
import eu.bolh.app.ui.finance.TransactionSheet
import eu.bolh.app.ui.goals.GoalDetailScreen
import eu.bolh.app.ui.goals.GoalDetailViewModel
import eu.bolh.app.ui.goals.GoalsScreen
import eu.bolh.app.ui.goals.GoalsViewModel
import eu.bolh.app.ui.theme.BolhTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Экраны целиком, с настоящими ViewModel и базой. Проверяется то, что
 * тестировщики видели глазами: ширина поля, прокрутка, лишние плашки.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h740dp")
class ScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: TestApp

    @Before
    fun setUp() {
        app = TestApp()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun SemanticsNodeInteraction.width(): Float =
        fetchSemanticsNode().boundsInRoot.width

    @Test
    fun goalCardShowsBudgetAndNoEmptyTempoTag() {
        val laptop = app.goal("Laptop")
        runBlocking {
            app.finance.baseCurrency = "AMD"
            app.finance.createJar(laptop, 21_850_000L)
        }
        val vm = GoalsViewModel(app.goals, app.habits, app.finance, app.container.settings)
        compose.setContent { BolhTheme { GoalsScreen(onCreate = {}, onOpenGoal = {}, viewModel = vm) } }

        waitForText("Laptop")
        waitForText("218,500", substring = true)
        assertTrue(compose.onAllNodesWithText("0 of", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("no habits").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun newTaskFieldTakesFullWidthOnSecondStage() {
        val goal = app.goal("Documents")
        runBlocking {
            app.goals.addStage(goal, "Collect papers")
            val second = app.goals.addStage(goal, "Visit the town hall and hand in the documents")
            app.goals.addTask(second, "Translate every document into Dutch (sworn translator)")
        }
        val vm = GoalDetailViewModel(app.goals, app.habits, app.finance, goal)
        compose.setContent { BolhTheme { GoalDetailScreen(onBack = {}, onEdit = {}, viewModel = vm) } }
        waitForText("Visit the town hall", substring = true)

        compose.onAllNodesWithText("Add a task")[1].performScrollTo().performClick()
        compose.waitForIdle()

        // Раньше поле второй задачи сжималось кнопкой до столбика в пару букв.
        val field = compose.onNode(hasSetTextAction() and isFocused())
        val ratio = field.width() / compose.onRoot().width()
        assertTrue("task field takes only ${(ratio * 100).toInt()}% of the screen", ratio > 0.6f)
    }

    @Test
    fun transactionSheetScrollsSoNoteCanRiseAboveKeyboard() {
        runBlocking { app.finance.createAccount("Card", "credit_card", AccountType.Card, 0L) }
        val vm = TransactionEditorViewModel(app.finance, app.goals)
        compose.setContent {
            BolhTheme { TransactionSheet(onDismiss = {}, onAddAccount = {}, viewModel = vm) }
        }
        waitForText("Date, note, goal")

        // Без прокрутки поле заметки оставалось под клавиатурой.
        val scrollable = compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText("Date, note, goal")))
        assertTrue(scrollable.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun oneTimePaymentHidesPeriodAndAsksForPaymentDate() {
        runBlocking { app.finance.createAccount("Card", "credit_card", AccountType.Card, 0L) }
        val vm = RecurringEditorViewModel(app.finance, app.container.reminders, null)
        compose.setContent { BolhTheme { RecurringEditorScreen(onDone = {}, viewModel = vm) } }
        waitForText("Card")
        waitForText("Repeat every N periods")

        compose.onNodeWithText("Once").performScrollTo().performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Repeat every N periods").fetchSemanticsNodes().isEmpty()
        }
        waitForText("Payment date")
        waitForText("One-time payment")
    }
}
