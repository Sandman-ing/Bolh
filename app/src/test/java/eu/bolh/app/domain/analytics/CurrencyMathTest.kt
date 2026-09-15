package eu.bolh.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Примерный общий итог по счетам в разных валютах (§6.3).
 *
 * Проверяется ровно то, на чём держится строка «Примерно всего»: пересчёт
 * через евро, валюты без дробной части и честный отказ, когда курса нет.
 */
class CurrencyMathTest {

    private val rates = mapOf(
        "EUR" to 1.0,
        "AMD" to 421.43,
        "USD" to 1.16,
        "JPY" to 162.0,
    )

    @Test
    fun `та же валюта не пересчитывается`() {
        assertEquals(12_345L, CurrencyMath.convert(12_345L, "EUR", "EUR", rates))
    }

    @Test
    fun `драмы в евро`() {
        // 800 драмов по 421,43 за евро — это 1,90 €.
        assertEquals(190L, CurrencyMath.convert(80_000L, "AMD", "EUR", rates))
    }

    @Test
    fun `евро в драмы`() {
        // 10 € — это 4 214,30 драма.
        assertEquals(421_430L, CurrencyMath.convert(1_000L, "EUR", "AMD", rates))
    }

    @Test
    fun `пара без евро считается через евро`() {
        // 10 $ → 8,62 € → 3 633,02 драма.
        assertEquals(363_302L, CurrencyMath.convert(1_000L, "USD", "AMD", rates))
    }

    @Test
    fun `валюта без дробной части`() {
        // 1 € — это 162 иены, и копеек у иены нет.
        assertEquals(162L, CurrencyMath.convert(100L, "EUR", "JPY", rates))
    }

    @Test
    fun `нет курса — нет суммы`() {
        assertNull(CurrencyMath.convert(100L, "EUR", "GBP", rates))
    }
}
