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
//
// Шаги яркости взяты заметными намеренно: карточка, отличающаяся от фона
// на пару процентов, на телефоне просто не читается как отдельный предмет.
val Ink = Color(0xFF0C0E11)
val Surface1 = Color(0xFF171B21)
val Surface2 = Color(0xFF1F242C)
val Divider = Color(0xFF272D35)

/** Обводка карточки: граница блока должна быть видна, а не угадываться. */
val Stroke = Color(0xFF3A434F)

/** Обводка вложенной плитки — тише внешней, чтобы рамки не спорили. */
val StrokeInner = Color(0xFF2E3640)
val TextPrimary = Color(0xFFE8EAED)
val TextSecondary = Color(0xFF9AA3AE)
val TextTertiary = Color(0xFF6B7480)
