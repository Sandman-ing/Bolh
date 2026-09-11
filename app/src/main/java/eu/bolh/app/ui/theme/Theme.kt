package eu.bolh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Раздел, в котором находится пользователь. Задаёт акцентный цвет всего экрана. */
val LocalSection = staticCompositionLocalOf { Section.Goals }

/** Статусные и нейтральные цвета. Одни и те же во всех разделах. */
val LocalStatusColors = staticCompositionLocalOf { BolhStatusColors() }

/** Тёмная ли сейчас тема. Нужно там, где цвет нельзя взять из схемы. */
val LocalDarkTheme = staticCompositionLocalOf { true }

/**
 * Тема Bolh.
 *
 * Тёмная остаётся значением по умолчанию: приложение открывают вечером, и
 * белый экран в темноте слепит. Светлая — выбор человека, а не подстройка под
 * время суток: переключение темы посреди дня сбивает больше, чем помогает (§9.4).
 */
@Composable
fun BolhTheme(
    section: Section = Section.Goals,
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val accent = section.accentOn(dark)
    val accentDeep = section.accentDeepOn(dark)
    val onAccent = if (dark) Ink else PaperSurface1
    val text = if (dark) TextPrimary else TextPrimaryLight
    val ground = if (dark) Ink else Paper
    val status = if (dark) BolhStatusColors() else BolhStatusColors.light()

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentDeep,
            onPrimaryContainer = text,
            secondary = accent,
            onSecondary = onAccent,
            background = ground,
            onBackground = text,
            surface = ground,
            onSurface = text,
            surfaceVariant = status.surfaceSunken,
            onSurfaceVariant = status.textSecondary,
            surfaceContainer = status.surfaceRaised,
            surfaceContainerHigh = status.surfaceSunken,
            // Обводка поля — заметная: с цветом разделителя рамка ввода
            // на светлом фоне почти исчезала, и поле переставало читаться
            // как поле.
            outline = status.stroke,
            outlineVariant = status.divider,
            error = status.problem,
            onError = onAccent,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentDeep,
            onPrimaryContainer = text,
            secondary = accent,
            onSecondary = onAccent,
            background = ground,
            onBackground = text,
            surface = ground,
            onSurface = text,
            surfaceVariant = status.surfaceSunken,
            onSurfaceVariant = status.textSecondary,
            surfaceContainer = status.surfaceRaised,
            surfaceContainerHigh = status.surfaceSunken,
            // Обводка поля — заметная: с цветом разделителя рамка ввода
            // на светлом фоне почти исчезала, и поле переставало читаться
            // как поле.
            outline = status.stroke,
            outlineVariant = status.divider,
            error = status.problem,
            onError = onAccent,
        )
    }

    CompositionLocalProvider(
        LocalSection provides section,
        LocalStatusColors provides status,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BolhTypography,
            content = content,
        )
    }
}
