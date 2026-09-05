package eu.bolh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Раздел, в котором находится пользователь. Задаёт акцентный цвет всего экрана. */
val LocalSection = staticCompositionLocalOf { Section.Goals }

/** Статусные и нейтральные цвета. Одни и те же во всех разделах. */
val LocalStatusColors = staticCompositionLocalOf { BolhStatusColors() }

/**
 * Тема Bolh. Тёмная — светлая отложена во вторую версию (§11),
 * поле темы в настройках уже заведено под неё.
 */
@Composable
fun BolhTheme(
    section: Section = Section.Goals,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = section.accent,
        onPrimary = Ink,
        primaryContainer = section.accentDeep,
        onPrimaryContainer = TextPrimary,
        secondary = section.accent,
        onSecondary = Ink,
        background = Ink,
        onBackground = TextPrimary,
        surface = Ink,
        onSurface = TextPrimary,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextSecondary,
        surfaceContainer = Surface1,
        surfaceContainerHigh = Surface2,
        outline = Divider,
        outlineVariant = Divider,
        error = ProblemRed,
        onError = Ink,
    )

    CompositionLocalProvider(
        LocalSection provides section,
        LocalStatusColors provides BolhStatusColors(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BolhTypography,
            content = content,
        )
    }
}
