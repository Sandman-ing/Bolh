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

    /**
     * Язык, пока человек не выбрал свой. Основной язык приложения английский:
     * так оно выглядит одинаково на любом телефоне, а русский и остальные
     * включаются в настройках одним касанием.
     */
    const val DEFAULT = "en"

    /**
     * Языки приложения с их собственными названиями.
     *
     * Название языка пишется на нём самом: человек, случайно включивший
     * незнакомый язык, должен найти свой, не зная ни слова вокруг.
     */
    val languages: List<Pair<String, String>> = listOf(
        "ru" to "Русский",
        "en" to "English",
        "nl" to "Nederlands",
        "de" to "Deutsch",
        "fr" to "Français",
    )

    private const val FILE = "language"
    private const val KEY = "code"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    fun set(context: Context, code: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, code)
            .apply()
    }

    /** Название выбранного языка для кнопки выбора. Пусто — язык системы. */
    fun nameOf(code: String): String? = languages.firstOrNull { it.first == code }?.second

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

    /**
     * Контекст с выбранным языком для того, что живёт вне экрана.
     *
     * Уведомления строятся из контекста приложения, а он знает только язык
     * системы: без этого напоминание приходило бы на другом языке, чем всё
     * остальное в Bolh.
     */
    fun localized(context: Context): Context {
        val code = get(context)
        if (code == SYSTEM) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(code)))
        return context.createConfigurationContext(configuration)
    }

    /** Локаль для сумм и дат вне экрана. */
    fun locale(context: Context): Locale {
        val code = get(context)
        if (code != SYSTEM) return Locale.forLanguageTag(code)
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }
}
