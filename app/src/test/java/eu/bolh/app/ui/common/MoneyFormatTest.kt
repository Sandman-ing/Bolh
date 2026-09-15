package eu.bolh.app.ui.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Показ сумм: буквенный код валюты не должен слипаться с числом.
 *
 * В английской локали код ставится перед суммой без пробела, и счёт в драмах
 * показывался как «AMD800».
 */
class MoneyFormatTest {

    @Test
    fun `код перед числом отделён неразрывным пробелом`() {
        assertEquals("AMD 800", Money.format(80_000L, "AMD", Locale.US))
    }

    @Test
    fun `минус остаётся впереди`() {
        assertEquals("-AMD 800", Money.format(-80_000L, "AMD", Locale.US))
    }

    @Test
    fun `знак валюты пробела не получает`() {
        assertEquals("€10", Money.format(1_000L, "EUR", Locale.US))
    }

    @Test
    fun `в русской локали код и так стоит после числа`() {
        assertFalse(Money.format(80_000L, "AMD", Locale.forLanguageTag("ru")).contains("AMD8"))
    }
}
