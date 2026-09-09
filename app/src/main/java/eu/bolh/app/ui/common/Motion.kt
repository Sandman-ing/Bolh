package eu.bolh.app.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Движение в Bolh.
 *
 * Приложение про факты, а не про эффекты, поэтому анимации короткие и
 * одинаковые: длинная анимация в ежедневном действии — это задержка, а
 * разнобой длительностей читается как несобранность. Один набор на всё.
 */
object Motion {

    /** Смена цвета и состояния: заметно, но не отвлекает. */
    val color = tween<androidx.compose.ui.graphics.Color>(durationMillis = 180)

    /** Появление и исчезновение содержимого. */
    const val CONTENT_MS = 220

    /** Перестройка размера блока при раскрытии. */
    val resize = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * Нажатие проминает элемент.
 *
 * Ответ на касание должен быть мгновенным и телесным: без него кнопка кажется
 * неживой, и человек жмёт второй раз. Пружина возвращает форму сама — обратная
 * анимация не нужна.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Источник взаимодействий для элемента, которому нужен только отклик на нажатие. */
@Composable
fun rememberPressSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }
