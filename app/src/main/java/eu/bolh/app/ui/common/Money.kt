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

    /**
     * Сколько знаков после запятой у валюты. У иены и драма их не столько же,
     * сколько у евро, и разбирать ввод «на глазок по двум» значит ошибиться
     * в сто раз на первой же сумме.
     */
    fun fractionDigitsOf(currencyCode: String): Int =
        runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
            .getOrNull()
            ?.coerceAtLeast(0)
            ?: 2

    fun format(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val fraction = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        val amount = BigDecimal.valueOf(minor, fraction)

        val format = NumberFormat.getCurrencyInstance(locale)
        if (currency != null) format.currency = currency
        format.minimumFractionDigits = if (minor % pow10(fraction) == 0L) 0 else fraction
        format.maximumFractionDigits = fraction
        // Буквенный код перед числом слипался с ним: «AMD800». Знаку «€»
        // пробел не нужен, а код без пробела читается как одно слово.
        return format.format(amount).replace(LEADING_CODE, "\$1\$2 ")
    }

    private val LEADING_CODE = Regex("^(-?)([A-Z]{3})(?=\\d)")

    /**
     * Разбор введённой суммы в минорные единицы.
     *
     * Поле часто заполняется тем, что мы сами и показали, а показываем мы по
     * правилам локали: в русской это неразрывный пробел между тысячами, во
     * французской — узкий неразрывный. Вырезать «обычный пробел» мало —
     * строка «2 105» тогда не разбирается, и правка молча не сохраняется.
     */
    fun parseMinor(text: String, fractionDigits: Int = 2): Long? =
        parseSignedMinor(text, fractionDigits)?.takeIf { it > 0L }

    /**
     * То же, но допускает ноль и минус: остаток на счёте бывает нулевым
     * и отрицательным, а сумма операции — нет.
     */
    fun parseSignedMinor(text: String, fractionDigits: Int = 2): Long? {
        val negative = text.trimStart().startsWith('-') || text.trimStart().startsWith('−')
        val cleaned = buildString {
            text.forEach { char ->
                when {
                    char.isDigit() -> append(char)
                    char == ',' || char == '.' -> append('.')
                    // Пробелы любых видов и знаки валюты отбрасываем.
                    else -> Unit
                }
            }
        }
        val value = cleaned.toDoubleOrNull() ?: return null
        val minor = Math.round(value * pow10(fractionDigits).toDouble())
        return if (negative) -minor else minor
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
