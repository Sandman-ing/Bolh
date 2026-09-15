package eu.bolh.app.domain.analytics

import java.util.Currency
import kotlin.math.roundToLong

/**
 * Пересчёт между валютами через евро (§6.3).
 *
 * Курсы хранятся как «сколько единиц валюты за один евро», поэтому любая пара
 * считается в два шага: из валюты в евро, из евро в нужную. Результат
 * приблизительный и так и показывается: курс меняется каждый день, а записанные
 * суммы остаются в своей валюте и не пересчитываются никогда.
 */
object CurrencyMath {

    /** Сумма в минорных единицах валюты [to] или null, если курса одной из валют нет. */
    fun convert(minor: Long, from: String, to: String, perEur: Map<String, Double>): Long? {
        if (from == to) return minor
        val fromRate = perEur[from] ?: return null
        val toRate = perEur[to] ?: return null
        val major = minor.toDouble() / pow10(fractionDigits(from))
        val converted = major / fromRate * toRate
        return (converted * pow10(fractionDigits(to))).roundToLong()
    }

    /** Знаков после запятой у валюты: у евро два, у иены ни одного. */
    fun fractionDigits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }
            .getOrNull()
            ?.takeIf { it >= 0 }
            ?: 2

    private fun pow10(digits: Int): Double {
        var result = 1.0
        repeat(digits) { result *= 10.0 }
        return result
    }
}
