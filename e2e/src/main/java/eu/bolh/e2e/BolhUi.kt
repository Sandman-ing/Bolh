package eu.bolh.e2e

import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
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

    /**
     * Запустить, как с иконки. Bolh может сразу спросить разрешение, например
     * на уведомления для напоминаний: системное окно ложится поверх, и тест
     * отвечает на него, как человек, прежде чем ждать сам экран.
     */
    fun launch() {
        device.pressHome()
        shell("am start -W -n $PACKAGE/.MainActivity")
        val opened = waitUntil(TIMEOUT) {
            allowSystemDialogs()
            device.hasObject(By.pkg(PACKAGE).depth(0)) && !device.hasObject(allowButton())
        }
        if (!opened) fail("Bolh did not open")
        device.waitForIdle()
    }

    /** Кнопка «Разрешить» системного окна. У Google и у чистого Android разные пакеты. */
    private fun allowButton(): BySelector = By.res(Pattern.compile(".*:id/permission_allow_button"))

    /** Системные окна разрешений: тест отвечает «разрешить», как человек. */
    fun allowSystemDialogs() {
        repeat(3) {
            val allow = device.findObject(allowButton()) ?: return
            Log.i(LOG_TAG, "allowing a system permission request")
            runCatching { allow.click() }
            device.waitForIdle()
        }
    }

    // --- Поиск ---------------------------------------------------------------

    /**
     * Условие поиска только в окнах Bolh. Условие — изменяемый объект, и
     * пакет в нём задаётся один раз, поэтому сужается копия, а не оригинал:
     * одно и то же условие ищется много раз подряд.
     */
    private fun inApp(selector: BySelector): BySelector = By.copy(selector).pkg(PACKAGE)

    fun find(selector: BySelector, timeout: Long = TIMEOUT): UiObject2? =
        device.wait(Until.findObject(inApp(selector)), timeout)

    /**
     * Что можно прокрутить. Сначала — в окне, с которым сейчас работают:
     * открытая шторка лежит поверх экрана, и крутить надо её, а не то,
     * что под ней.
     */
    private fun scrollables(): List<UiObject2> {
        val active = runCatching { device.findWindow(By.Window.active(true)) }.getOrNull()
        val inActive = runCatching {
            active?.takeIf { it.packageName == PACKAGE }?.findObjects(By.scrollable(true))
        }.getOrNull().orEmpty()
        return inActive.ifEmpty { device.findObjects(inApp(By.scrollable(true))) }
    }

    /**
     * Найти, при нужде докрутив экран вниз. Вверх тест не крутит сам: у шторки
     * такой жест — это «закрыть», и она закрылась бы посреди проверки.
     */
    fun reveal(selector: BySelector, what: String, up: Boolean = false): UiObject2 {
        find(selector, SHORT)?.let { return it }
        hideKeyboard()
        val direction = if (up) Direction.UP else Direction.DOWN
        repeat(2) {
            for (scrollable in scrollables()) {
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
     * Первая надпись [text] ниже того, что вернёт [anchor]: среди одинаковых
     * кнопок нужна та, что под своим полем. Если она ушла за край экрана,
     * экран понемногу докручивается.
     */
    fun textBelow(text: String, anchor: () -> UiObject2): UiObject2 {
        repeat(5) {
            val top = anchor().visibleBounds.bottom
            allTexts(text).firstOrNull { it.visibleBounds.top >= top }?.let { return it }
            val scrollable = scrollables().firstOrNull() ?: return@repeat
            runCatching { scrollable.scroll(Direction.DOWN, 0.3f) }
            device.waitForIdle()
        }
        fail("No \"$text\" below its field")
        error("unreachable")
    }

    /**
     * Нажать, как человек. Клавиатура сначала убирается: кнопка под ней есть
     * в дереве экрана, и нажатие пришлось бы на клавишу, а не на неё.
     * Экран Compose пересобирает узлы, и найденный миг назад узел может
     * устареть — тогда он ищется заново.
     */
    fun click(target: UiObject2, selector: BySelector? = null) {
        var node = target
        if (keyboardBounds() != null) {
            hideKeyboard()
            if (selector != null) node = reveal(selector, selector.toString())
        }
        repeat(3) { attempt ->
            try {
                node.click()
                device.waitForIdle()
                return
            } catch (stale: StaleObjectException) {
                if (selector == null || attempt == 2) throw stale
                Log.i(LOG_TAG, "stale node, looking again: $selector")
                device.waitForIdle()
                node = reveal(selector, selector.toString())
            }
        }
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
        val selector = By.text(Pattern.compile(regex, Pattern.DOTALL))
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
            runCatching {
                edit.text == label || edit.hint == label || edit.contentDescription == label ||
                    edit.findObject(By.text(label)) != null
            }.getOrDefault(false)
        }
        match()?.let { return it }
        hideKeyboard()
        // Поле ниже края экрана: докручиваем понемногу и смотрим снова.
        repeat(8) {
            val scrollable = scrollables().firstOrNull() ?: return@repeat
            runCatching { scrollable.scroll(Direction.DOWN, 0.5f) }
            device.waitForIdle()
            match()?.let { return it }
        }
        fail("Field not found: $label")
        error("unreachable")
    }

    /**
     * Нажать в поле и вписать текст, как человек, а потом убрать клавиатуру:
     * она выезжает не сразу, и иначе следующее нажатие пришлось бы на неё.
     */
    fun fill(label: String, value: String) {
        val edit = field(label)
        edit.click()
        device.waitForIdle()
        field(label).text = value
        device.waitForIdle()
        hideKeyboard(expectIt = true)
    }

    fun focusedField(): UiObject2 =
        find(By.clazz(EDIT_TEXT).focused(true)) ?: run { fail("No focused field"); error("unreachable") }

    /**
     * Убрать клавиатуру кнопкой «назад». [expectIt] — поле только что получило
     * фокус, и клавиатура ещё выезжает: её нужно дождаться, иначе она
     * появится уже после проверки.
     */
    fun hideKeyboard(expectIt: Boolean = false) {
        if (expectIt) waitUntil(KEYBOARD_APPEARS) { keyboardBounds() != null }
        if (keyboardBounds() == null) return
        device.pressBack()
        if (!waitUntil(SHORT) { keyboardBounds() == null }) Log.w(LOG_TAG, "keyboard is still shown after back")
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MS)
    }

    /** Где сейчас клавиатура. Пусто — клавиатуры на экране нет. */
    fun keyboardBounds(): Rect? {
        val window = runCatching {
            device.findWindow(By.Window.type(AccessibilityWindowInfo.TYPE_INPUT_METHOD))
        }.getOrNull()
        val fromWindow = runCatching { window?.visibleBounds }.getOrNull()
        if (fromWindow != null && fromWindow.height() > 0) return fromWindow
        // Запасной путь: узлы самой клавиатуры, по её пакету.
        val ime = imePackage() ?: return null
        val keys = device.findObjects(By.pkg(ime))
            .filter { runCatching { it.visibleBounds.height() > 0 }.getOrDefault(false) }
        if (keys.isEmpty()) return null
        return Rect(keys.first().visibleBounds).also { area -> keys.forEach { area.union(it.visibleBounds) } }
    }

    private var ime: String? = null

    private fun imePackage(): String? {
        ime?.let { return it }
        val id = shell("settings get secure default_input_method").trim()
        return id.substringBefore('/').takeIf { it.isNotEmpty() && it != "null" }?.also { ime = it }
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
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(100)
        }
        return runCatching(condition).getOrDefault(false)
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
            // Журнал режет записи по переводам строк, поэтому дерево идёт одной строкой.
            out.toString().replace('\n', ' ').chunked(1_000).forEachIndexed { index, chunk ->
                Log.i(DUMP_TAG, "$name #$index $chunk")
            }
        }
    }

    companion object {
        const val PACKAGE = "eu.bolh.app"
        const val TIMEOUT = 15_000L
        const val SHORT = 3_000L
        const val KEYBOARD_APPEARS = 2_500L
        const val SETTLE_MS = 300L
        const val EDIT_TEXT = "android.widget.EditText"
        const val DUMP_DIR = "/data/local/tmp/e2e"
        const val DUMP_TAG = "E2E-DUMP"
        const val LOG_TAG = "E2E"
    }
}
