package eu.bolh.e2e

import androidx.test.uiautomator.By

/**
 * Шаги, из которых складываются сценарии: завести цель, привычку, счёт,
 * записать трату. Каждый шаг идёт через экран, как у человека, и кончается
 * там, откуда начался, — на своей вкладке.
 */
class Flows(private val ui: BolhUi) {

    fun createGoal(name: String) {
        ui.tab("Goals")
        ui.tapDesc("Create")
        ui.expectText("New goal")
        ui.fill("Name", name)
        ui.tap("Save")
        ui.expectShownText(name)
    }

    fun createHabit(name: String) {
        ui.tab("Habits")
        ui.tapDesc("Create")
        ui.expectText("New habit")
        ui.fill("Name", name)
        ui.tap("Save")
        ui.expectShownText(name)
    }

    /** Отметить привычку кругом справа от названия. */
    fun markHabit(name: String) {
        ui.tab("Habits")
        val title = ui.text(name)
        val row = generateSequence(title.parent) { it.parent }.firstOrNull { it.isClickable }
            ?: error("Habit row \"$name\" is not tappable")
        // Круг — отдельная кнопка внутри строки, самая правая из них.
        val circle = row.findObjects(By.clickable(true))
            .filter { it.visibleBounds != row.visibleBounds }
            .maxByOrNull { it.visibleBounds.right }
            ?: error("No mark button in habit row \"$name\"")
        ui.click(circle)
        ui.expectDesc("Mark done")
    }

    fun createAccount(name: String) {
        ui.tab("Finance")
        if (ui.find(By.text("Add your first account"), BolhUi.SHORT) != null) {
            ui.tap("Add your first account")
        } else {
            // Следующий счёт заводится из шторки траты, рядом с выбором счёта.
            ui.tapDesc("Create")
            ui.tap("Add account")
        }
        ui.expectText("New account")
        ui.fill("Name", name)
        ui.tap("Save")
        ui.expectShownText(name)
    }

    /** Трата со страницы счёта: счёт в шторке уже выбран. */
    fun recordExpense(account: String, amount: String, note: String? = null) {
        ui.tab("Finance")
        ui.tap(account)
        ui.tapDesc("Record")
        ui.fill("Amount", amount)
        if (note != null) {
            ui.tap("Date, note, goal")
            ui.fill("Note", note)
        }
        ui.tap("Record")
        ui.expectNoText("Date, note, goal", BolhUi.TIMEOUT)
        ui.back()
        ui.expectText(account)
    }

    fun addPayment(name: String, amount: String, once: Boolean) {
        ui.tab("Finance")
        ui.tap("Payments")
        ui.tap("Add a payment")
        ui.fill("What for", name)
        ui.fill("Amount", amount)
        if (once) {
            ui.tap("Once")
            ui.expectText("Payment date")
        }
        ui.tap("Save")
        // Список платежей, а не поле редактора, где имя ещё набрано.
        ui.expectShownText(name)
    }

    fun openSettings() {
        ui.tab("Stats")
        ui.tapDesc("Settings")
        ui.expectText("Language")
    }
}
