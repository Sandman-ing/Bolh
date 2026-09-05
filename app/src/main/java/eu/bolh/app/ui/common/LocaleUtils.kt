package eu.bolh.app.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import eu.bolh.app.data.prefs.SettingsStore
import java.util.Locale

/**
 * Локаль, за которой Compose следит.
 *
 * `Locale.getDefault()` внутри composable читается один раз и не пересобирает
 * экран при смене языка: приложение на двух языках осталось бы с датами
 * и суммами от прежнего (§9.6).
 */
@Composable
fun rememberLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        configuration.locales.takeIf { it.size() > 0 }?.get(0) ?: Locale.getDefault()
    }
}

/**
 * Контекст с выбранным языком.
 *
 * Язык переключается внутри приложения, а не только в настройках телефона:
 * человек может держать систему на одном языке, а приложение на другом.
 */
@Composable
fun rememberLocalizedContext(language: String): Context {
    val base = LocalContext.current
    // Исходная конфигурация берётся у Compose, а не у ресурсов контекста:
    // у ресурсов она может оказаться устаревшей после смены настроек системы.
    val current = LocalConfiguration.current
    return remember(language, base, current) {
        if (language == SettingsStore.LANGUAGE_SYSTEM) {
            base
        } else {
            val configuration = Configuration(current)
            configuration.setLocales(LocaleList(Locale.forLanguageTag(language)))
            base.createConfigurationContext(configuration)
        }
    }
}
