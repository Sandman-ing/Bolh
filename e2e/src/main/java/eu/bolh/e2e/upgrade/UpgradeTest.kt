package eu.bolh.e2e.upgrade

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.bolh.e2e.BolhRule
import eu.bolh.e2e.Flows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Обновление поверх прежней версии. Сначала прежняя версия получает данные
 * через экран, потом поверх ставится новая, и всё должно остаться на месте.
 *
 * Запускаются по одному, между ними — установка новой версии:
 * SeedBeforeUpdateTest на прежней, VerifyAfterUpdateTest на новой.
 */
object Upgrade {
    const val GOAL = "Upgrade goal"
    const val HABIT = "Upgrade habit"
    const val ACCOUNT = "Upgrade card"
    const val NOTE = "Before update"
    const val PAYMENT = "Upgrade rent"
}

@RunWith(AndroidJUnit4::class)
class SeedBeforeUpdateTest {

    @get:Rule
    val bolh = BolhRule(freshInstall = true)

    @Test
    fun seedDataInPreviousVersion() {
        val ui = bolh.ui
        val flows = Flows(ui)
        flows.createGoal(Upgrade.GOAL)
        flows.createHabit(Upgrade.HABIT)
        flows.markHabit(Upgrade.HABIT)
        flows.createAccount(Upgrade.ACCOUNT)
        flows.recordExpense(Upgrade.ACCOUNT, "42", note = Upgrade.NOTE)
        flows.addPayment(Upgrade.PAYMENT, "55", once = false)
        // Тема лежит не в базе, а в настройках: её тоже надо пронести.
        flows.openSettings()
        ui.tap("Light")
        assertTrue("light theme should be light", ui.lightShare() > 0.6f)
    }
}

@RunWith(AndroidJUnit4::class)
class VerifyAfterUpdateTest {

    @get:Rule
    val bolh = BolhRule(freshInstall = false)

    @Test
    fun dataSurvivesUpdate() {
        val ui = bolh.ui
        assertTrue("theme should still be light after the update", ui.lightShare() > 0.6f)

        ui.tab("Goals")
        ui.expectText(Upgrade.GOAL)
        ui.tab("Habits")
        ui.expectText(Upgrade.HABIT)
        ui.expectDesc("Mark done")

        ui.tab("Finance")
        ui.expectText(Upgrade.ACCOUNT)
        ui.tap("All transactions")
        ui.expectText(Upgrade.NOTE)
        ui.back()
        ui.tap("Payments")
        ui.expectText(Upgrade.PAYMENT)

        // Новая версия не только читает старые данные, но и пишет поверх них.
        ui.tap("Record")
        ui.expectText(Upgrade.PAYMENT)
        ui.tab("Stats")
        ui.expectTextContains("97")
    }
}
