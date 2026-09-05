package eu.bolh.app.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Деньги хранятся в минорных единицах целым числом: дробные типы на сложении
 * тысячи операций дают копеечное расхождение, которое потом невозможно объяснить.
 *
 * Показываются суммы по правилам локали устройства, но всегда с явной валютой:
 * в мультивалютном приложении «1 240» без знака — это не число, а загадка.
 */
object Money {

    fun format(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val fraction = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        val amount = BigDecimal.valueOf(minor, fraction)

        val format = NumberFormat.getCurrencyInstance(locale)
        if (currency != null) format.currency = currency
        format.minimumFractionDigits = if (minor % pow10(fraction) == 0L) 0 else fraction
        format.maximumFractionDigits = fraction
        return format.format(amount)
    }

    /** Сумма без знака валюты — для колонок, где валюта названа в заголовке. */
    fun formatPlain(minor: Long, fractionDigits: Int = 2, locale: Locale = Locale.getDefault()): String {
        val amount = BigDecimal.valueOf(minor, fractionDigits)
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = if (minor % pow10(fractionDigits) == 0L) 0 else fractionDigits
        format.maximumFractionDigits = fractionDigits
        return format.format(amount)
    }

    private fun pow10(digits: Int): Long {
        var result = 1L
        repeat(digits) { result *= 10L }
        return result
    }
}
