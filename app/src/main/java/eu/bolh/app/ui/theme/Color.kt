package eu.bolh.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Цвет в Bolh — язык, а не украшение (документ продукта, §9.3).
 * Каждый цвет означает раздел или статус, и значение не меняется от экрана к экрану.
 */

// Разделы. Три соседних цвета на круге: разные, но родные.
val GoalsTeal = Color(0xFF5DCAA5)
val HabitsViolet = Color(0xFF7F77DD)
val HabitsVioletDeep = Color(0xFF534AB7)
val FinanceBlue = Color(0xFF85B7EB)
val FinanceBlueDeep = Color(0xFF378ADD)

// Статусы. Перебивают цвет раздела.
val AttentionOrange = Color(0xFFEF9F27)
val ProblemRed = Color(0xFFE24B4A)
val SuccessGreen = Color(0xFF97C459)

// Нейтральная основа тёмной темы.
val Ink = Color(0xFF0F1114)
val Surface1 = Color(0xFF16191D)
val Surface2 = Color(0xFF1E2228)
val Divider = Color(0xFF2A2F36)
val TextPrimary = Color(0xFFE8EAED)
val TextSecondary = Color(0xFF9AA3AE)
val TextTertiary = Color(0xFF6B7480)
