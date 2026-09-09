package eu.bolh.app.data.prefs

import android.content.Context

/**
 * Валюта приложения.
 *
 * Одна на всё и без пересчёта: курсы отложены во вторую версию (§6.3), а
 * человеку нужно записывать траты в своих деньгах уже сейчас. Хранится в
 * обычных настройках, потому что читается синхронно при старте — репозиторий
 * форматирует суммы с первой же отрисовки.
 */
object CurrencyPrefs {

    /**
     * Часто выбираемые — они же первые в списке. Остальные валюты мира
     * берутся из системы: держать свой перечень значит однажды его забыть.
     */
    val frequent: List<String> = listOf("EUR", "USD", "GBP", "RUB", "AMD")

    /** Все валюты, известные системе, с частыми наверху. */
    fun all(): List<String> {
        val rest = java.util.Currency.getAvailableCurrencies()
            .map { it.currencyCode }
            .filter { it.length == 3 && it !in frequent }
            .sorted()
        return frequent + rest
    }

    /** Человеческое имя валюты на языке интерфейса. */
    fun nameOf(code: String, locale: java.util.Locale): String =
        runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }
            .getOrDefault(code)

    const val DEFAULT = "EUR"

    private const val FILE = "currency"
    private const val KEY = "code"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    fun set(context: Context, code: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, code)
            .apply()
    }
}
