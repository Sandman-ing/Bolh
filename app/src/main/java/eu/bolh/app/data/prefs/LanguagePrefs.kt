package eu.bolh.app.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Язык интерфейса.
 *
 * Хранится в обычных настройках, а не в DataStore, по одной причине: его нужно
 * прочитать синхронно в момент создания экрана, до первой отрисовки. Асинхронное
 * чтение здесь означало бы вспышку чужого языка при каждом запуске.
 */
object LanguagePrefs {

    const val SYSTEM = "system"

    private const val FILE = "language"
    private const val KEY = "code"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, code: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, code)
            .apply()
    }

    /**
     * Контекст экрана с выбранным языком.
     *
     * Подменяется только конфигурация, а сам контекст остаётся тем же Activity:
     * иначе Compose перестаёт находить Activity и системные диалоги — выбор
     * файла, например — падают на ровном месте.
     */
    fun wrap(base: Context): Context {
        val code = get(base)
        if (code == SYSTEM) return base

        val configuration = Configuration(base.resources.configuration)
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }
}
