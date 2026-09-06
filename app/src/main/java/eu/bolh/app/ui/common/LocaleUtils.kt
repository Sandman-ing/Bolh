package eu.bolh.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
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
