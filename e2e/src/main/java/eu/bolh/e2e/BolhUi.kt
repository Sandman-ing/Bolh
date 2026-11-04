package eu.bolh.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertNull
import org.junit.Assert.fail

/**
 * Руки и глаза теста: нажимает по тексту, как человек, заполняет поля,
 * прокручивает до нужного места. Приложение видно тесту только снаружи.
 *
 * Всё ищется только в окнах Bolh: у клавиатуры и системы свои надписи,
 * и нажатие по чужой кнопке испортило бы тест незаметно.
 */
class BolhUi {

    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun shell(command: String): String = device.executeShellCommand(command)

    /** Чистая установка: данных нет, как у нового пользователя. */
    fun clearData() {
        shell("pm clear $PACKAGE")
    }

    fun launch() {
        device.pressHome()
        shell("am start -W -n $PACKAGE/.MainActivity")
        if (!device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT)) {
            fail("Bolh did not open")
        }
        allowSystemDialogs()
        device.waitForIdle()
    }

    /** Системные окна разрешений: тест отвечает «разрешить», как человек. */
    fun allowSystemDialogs() {
        repeat(2) {
            val allow = device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
                ?: device.findObject(By.res("com.android.packageinstaller:id/permission_allow_button"))
                ?: return
            allow.click()
            device.waitForIdle()
        }
    }

    // --- Поиск ---------------------------------------------------------------

    private fun inApp(selector: BySelector): BySelector = selector.pkg(PACKAGE)

    fun find(selector: BySelector, timeout: Long = TIMEOUT): UiObject2? =
        device.wait(Until.findObject(inApp(selector)), timeout)

    /**
     * Найти, при нужде докрутив экран вниз. Вверх тест не крутит сам: у шторки
     * такой жест — это «закрыть», и она закрылась бы посреди проверки.
     */
    fun reveal(selector: BySelector, what: String, up: Boolean = false): UiObject2 {
        find(selector, SHORT)?.let { return it }
        val direction = if (up) Direction.UP else Direction.DOWN
        repeat(2) {
            for (scrollable in device.findObjects(inApp(By.scrollable(true)))) {
                runCatching { scrollable.scrollUntil(direction, Until.findObject(inApp(selector))) }
                    .getOrNull()
                    ?.let { return it }
            }
        }
        find(selector, SHORT)?.let { return it }
        fail("Not found on screen: $what")
        error("unreachable")
    }

    fun text(text: String, up: Boolean = false): UiObject2 = reveal(By.text(text), "text \"$text\"", up)

    fun textContains(part: String): UiObject2 = reveal(By.textContains(part), "text containing \"$part\"")

    /** Все надписи с этим текстом сверху вниз — когда одинаковых кнопок несколько. */
    fun allTexts(text: String): List<UiObject2> =
        device.findObjects(inApp(By.text(text))).sortedBy { it.visibleBounds.top }

    /**
     * Нажать, как человек. Клавиатура сначала убирается: кнопка под ней есть
     * в дереве экрана, и нажатие пришлось бы на клавишу, а не на неё.
     */
    fun click(target: UiObject2, selector: BySelector? = null) {
        var node = target
        if (keyboardBounds() != null) {
            hideKeyboard()
            if (selector != null) node = reveal(selector, selector.toString())
        }
        node.click()
        device.waitForIdle()
    }

    fun tap(text: String, up: Boolean = false) {
        click(text(text, up), By.text(text))
    }

    fun tapDesc(description: String) {
        click(reveal(By.desc(description), "button \"$description\""), By.desc(description))
    }

    /**
     * Вкладка внизу. Подпись значка Material прячет, когда под ним есть текст,
     * а заголовок экрана бывает тем же словом, поэтому берётся самая нижняя
     * надпись — она и есть вкладка.
     */
    fun tabNode(label: String): UiObject2? {
        val limit = device.displayHeight * 4 / 5
        waitUntil(TIMEOUT) { allTexts(label).any { it.visibleBounds.top >= limit } }
        return allTexts(label).lastOrNull { it.visibleBounds.top >= limit }
    }

    fun tab(label: String) {
        hideKeyboard()
        val tab = tabNode(label) ?: run { fail("No tab \"$label\""); error("unreachable") }
        tab.click()
        device.waitForIdle()
    }

    fun expectTab(label: String) {
        if (tabNode(label) == null) fail("No tab \"$label\"")
    }

    fun longTap(text: String) {
        hideKeyboard()
        text(text).longClick()
        device.waitForIdle()
    }

    fun expectText(text: String, timeout: Long = TIMEOUT) {
        if (find(By.text(text), timeout) == null) reveal(By.text(text), "text \"$text\"")
    }

    fun expectTextContains(part: String, timeout: Long = TIMEOUT) {
        if (find(By.textContains(part), timeout) == null) reveal(By.textContains(part), "text containing \"$part\"")
    }

    fun expectTextMatches(regex: String, timeout: Long = TIMEOUT) {
        val selector = By.text(java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL))
        if (find(selector, timeout) == null) reveal(selector, "text matching /$regex/")
    }

    /** Надпись есть, и это не поле ввода, где она ещё набрана. */
    fun expectShownText(text: String, timeout: Long = TIMEOUT) {
        val shown = waitUntil(timeout) {
            device.findObjects(inApp(By.text(text))).any { it.className != EDIT_TEXT }
        }
        if (!shown) fail("Not shown outside a text field: \"$text\"")
    }

    fun expectDesc(description: String, timeout: Long = TIMEOUT) {
        if (find(By.desc(description), timeout) == null) fail("Not found on screen: \"$description\"")
    }

    fun expectNoText(text: String, timeout: Long = SHORT) {
        device.wait(Until.gone(inApp(By.text(text))), timeout)
        assertNull("Should be gone from screen: \"$text\"", device.findObject(inApp(By.text(text))))
    }

    fun expectNoTextContains(part: String, timeout: Long = SHORT) {
        device.wait(Until.gone(inApp(By.textContains(part))), timeout)
        assertNull("Should be gone from screen: \"$part\"", device.findObject(inApp(By.textContains(part))))
    }

    // --- Поля ввода ----------------------------------------------------------

    /**
     * Поле по его подписи. Compose отдаёт подпись по-разному в разных версиях,
     * поэтому подпись ищется в тексте, подсказке, описании и вложенных узлах.
     */
    fun field(label: String): UiObject2 {
        fun match(): UiObject2? = device.findObjects(inApp(By.clazz(EDIT_TEXT))).firstOrNull { edit ->
            edit.text == label || edit.hint == label || edit.contentDescription == label ||
                edit.findObject(By.text(label)) != null
        }
        match()?.let { return it }
        // Поле ниже края экрана: докручиваем понемногу и смотрим снова.
        repeat(8) {
            val scrollable = device.findObjects(inApp(By.scrollable(true))).firstOrNull() ?: return@repeat
            scrollable.scroll(Direction.DOWN, 0.5f)
            device.waitForIdle()
            match()?.let { return it }
        }
        fail("Field not found: $label")
        error("unreachable")
    }

    /** Нажать в поле и вписать текст. Клавиатура остаётся открытой, как у человека. */
    fun fill(label: String, value: String) {
        val edit = field(label)
        edit.click()
        device.waitForIdle()
        edit.text = value
        device.waitForIdle()
    }

    fun focusedField(): UiObject2 =
        find(By.clazz(EDIT_TEXT).focused(true)) ?: run { fail("No focused field"); error("unreachable") }

    fun hideKeyboard() {
        if (keyboardBounds() != null) {
            device.pressBack()
            waitUntil(SHORT) { keyboardBounds() == null }
            device.waitForIdle()
        }
    }

    /** Где сейчас клавиатура. Пусто — клавиатуры на экране нет. */
    fun keyboardBounds(): Rect? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        if (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
        }
        val ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?: return null
        val bounds = Rect().also { ime.getBoundsInScreen(it) }
        return bounds.takeIf { it.height() > 0 }
    }

    /** Клавиатура выезжает с анимацией: ждём, пока она встанет на место. */
    fun awaitKeyboard(): Rect {
        var last: Rect? = null
        var stableSince = 0L
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            val now = keyboardBounds()
            if (now != null && now == last) {
                if (SystemClock.uptimeMillis() - stableSince > 700) return now
            } else {
                stableSince = SystemClock.uptimeMillis()
            }
            last = now
            SystemClock.sleep(100)
        }
        fail("The keyboard did not appear")
        error("unreachable")
    }

    fun back() {
        hideKeyboard()
        device.pressBack()
        device.waitForIdle()
    }

    fun waitUntil(timeout: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    // --- Экран целиком ------------------------------------------------------

    /**
     * Доля светлых точек на экране. По ней видно тему: на светлой фон белый,
     * на тёмной — почти чёрный, и карточки этого не перевешивают.
     */
    fun lightShare(): Float {
        device.waitForIdle()
        SystemClock.sleep(600) // смена цвета идёт анимацией
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: run { fail("No screenshot"); error("unreachable") }
        var light = 0
        var total = 0
        // Строка состояния и панель жестов рисуются системой, их не считаем.
        val top = shot.height / 10
        val bottom = shot.height * 9 / 10
        for (i in 0 until 12) {
            for (j in 0 until 12) {
                val x = shot.width * (2 * i + 1) / 24
                val y = top + (bottom - top) * (2 * j + 1) / 24
                val pixel = shot.getPixel(x, y)
                val luminance = (0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) +
                    0.0722 * Color.blue(pixel)) / 255.0
                if (luminance > 0.5) light++
                total++
            }
        }
        shot.recycle()
        return light.toFloat() / total
    }

    // --- Отладка -------------------------------------------------------------

    /** Скриншот и дерево экрана: по ним видно, что тест увидел в момент сбоя. */
    fun dump(name: String) {
        runCatching {
            shell("mkdir -p $DUMP_DIR")
            shell("screencap -p $DUMP_DIR/$name.png")
        }
        runCatching {
            val out = ByteArrayOutputStream()
            device.dumpWindowHierarchy(out)
            out.toString().chunked(1_000).forEachIndexed { index, chunk ->
                Log.i(DUMP_TAG, "$name #$index $chunk")
            }
        }
    }

    companion object {
        const val PACKAGE = "eu.bolh.app"
        const val TIMEOUT = 15_000L
        const val SHORT = 3_000L
        const val EDIT_TEXT = "android.widget.EditText"
        const val DUMP_DIR = "/data/local/tmp/e2e"
        const val DUMP_TAG = "E2E-DUMP"
    }
}
