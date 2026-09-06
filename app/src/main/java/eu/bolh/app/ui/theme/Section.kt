package eu.bolh.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Раздел приложения. Несёт свой цвет — по нему человек понимает, где находится. */
enum class Section(val accent: Color, val accentDeep: Color) {
    Goals(GoalsTeal, GoalsTeal),
    Habits(HabitsViolet, HabitsVioletDeep),
    Finance(FinanceBlue, FinanceBlueDeep),

    /** У статистики своего цвета нет: она не про себя, а про все три раздела. */
    Stats(TextSecondary, TextSecondary),
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
)
