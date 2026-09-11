package eu.bolh.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Раздел приложения. Несёт свой цвет — по нему человек понимает, где находится.
 *
 * Цвета два: на тёмном фоне работает светлый акцент, на светлом он выцветает
 * и перестаёт читаться как текст, поэтому там свой, более тёмный (§9.3).
 */
enum class Section(
    val accent: Color,
    val accentDeep: Color,
    val accentLight: Color,
    val accentDeepLight: Color,
) {
    Goals(GoalsTeal, GoalsTeal, GoalsTealInk, GoalsTealInk),
    Habits(HabitsViolet, HabitsVioletDeep, HabitsVioletInk, HabitsVioletDeepInk),
    Finance(FinanceBlue, FinanceBlueDeep, FinanceBlueInk, FinanceBlueDeepInk),

    /** У статистики своего цвета нет: она не про себя, а про все три раздела. */
    Stats(TextSecondary, TextSecondary, TextSecondaryLight, TextSecondaryLight);

    fun accentOn(dark: Boolean): Color = if (dark) accent else accentLight

    fun accentDeepOn(dark: Boolean): Color = if (dark) accentDeep else accentDeepLight
}

/** Семантические цвета, одинаковые во всех разделах. */
@Immutable
data class BolhStatusColors(
    val attention: Color = AttentionOrange,
    val problem: Color = ProblemRed,
    val success: Color = SuccessGreen,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val divider: Color = Divider,
    val stroke: Color = Stroke,
    val strokeInner: Color = StrokeInner,
    val surfaceRaised: Color = Surface1,
    val surfaceSunken: Color = Surface2,
) {
    companion object {
        /**
         * Светлый набор. Оттенки подобраны, а не выведены инверсией: на белом
         * та же сетка яркостей даёт серую кашу, в которой карточка не
         * отличается от фона.
         */
        fun light(): BolhStatusColors = BolhStatusColors(
            attention = AttentionOrangeInk,
            problem = ProblemRedInk,
            success = SuccessGreenInk,
            textSecondary = TextSecondaryLight,
            textTertiary = TextTertiaryLight,
            divider = PaperDivider,
            stroke = PaperStroke,
            strokeInner = PaperStrokeInner,
            surfaceRaised = PaperSurface1,
            surfaceSunken = PaperSurface2,
        )
    }
}
