package eu.bolh.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Шаг сетки — 4dp. У каждого раздела свой характер (§9.2), и различается он
 * прежде всего плотностью: цели дышат, финансы плотные как выписка.
 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Поля экрана слева и справа — одинаковые во всех разделах. */
    val screenPadding = 20.dp

    /** Вертикальный отступ строки привычки: зона нажатия должна быть крупной. */
    val habitRowVertical = 18.dp

    /** Воздух между карточками целей. */
    val goalCardGap = 14.dp

    /** Плотная строка финансовой выписки. */
    val financeRowVertical = 12.dp
}

/**
 * Скругления. Крупный радиус карточки — характер приложения: блок должен
 * читаться как отдельный предмет, а не как кусок фона.
 */
object Radius {
    val card = 22.dp
    val block = 16.dp
    val control = 14.dp
    val chip = 12.dp
}

/** Минимальная зона нажатия для ежедневного действия. */
val TapTargetMin = 48.dp
