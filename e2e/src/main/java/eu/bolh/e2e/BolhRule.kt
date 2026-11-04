package eu.bolh.e2e

import org.junit.Assert.fail
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Обвязка каждого теста: журнал падений обнуляется до теста и читается после.
 * Вылет приложения валит тест, даже если на экране всё успели увидеть.
 * При сбое сохраняются скриншот и дерево экрана.
 */
class BolhRule(private val freshInstall: Boolean = true) : TestWatcher() {

    val ui = BolhUi()

    override fun starting(description: Description) {
        ui.shell("logcat -b crash -c")
        if (freshInstall) ui.clearData()
        ui.launch()
    }

    override fun succeeded(description: Description) {
        val crashes = ui.shell("logcat -d -b crash")
        if (crashes.contains(BolhUi.PACKAGE)) {
            ui.dump(description.methodName + "-crash")
            fail("Bolh crashed during the test:\n" + crashes.takeLast(4_000))
        }
    }

    override fun failed(e: Throwable, description: Description) {
        ui.dump(description.methodName)
    }
}
