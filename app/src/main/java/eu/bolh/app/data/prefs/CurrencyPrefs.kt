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

    /** Валюты без пересчёта: выбор один раз и на всё приложение. */
    val supported: List<String> = listOf("EUR", "USD", "GBP", "RUB", "AMD")

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
