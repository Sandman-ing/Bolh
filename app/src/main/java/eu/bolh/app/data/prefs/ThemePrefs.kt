package eu.bolh.app.data.prefs

import android.content.Context

/**
 * Тема оформления.
 *
 * Хранится там же и так же, как язык: читается синхронно при создании экрана,
 * до первой отрисовки. Асинхронное чтение означало бы вспышку чужой темы при
 * каждом запуске — а это ровно то, ради чего тёмную тему и выбирают (§9.4).
 */
object ThemePrefs {

    const val SYSTEM = "system"
    const val DARK = "dark"
    const val LIGHT = "light"

    private const val FILE = "theme"
    private const val KEY = "mode"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, DARK) ?: DARK

    fun set(context: Context, mode: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode)
            .apply()
    }

    /**
     * Тёмная ли тема сейчас.
     *
     * Значение по умолчанию — тёмная, а не системная: приложение открывают
     * вечером, и белый экран в темноте слепит. Системную выбирают осознанно.
     */
    fun isDark(mode: String, systemInDark: Boolean): Boolean = when (mode) {
        LIGHT -> false
        SYSTEM -> systemInDark
        else -> true
    }
}
