package eu.bolh.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToLong

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

/**
 * Отметка о выполнении: короткий хлопок.
 *
 * Сила отклика растёт с весом события — задача меньше привычки, привычка
 * меньше цели, а цель получает [CompletionPulse] во весь экран. Одна галочка
 * без движения не отличается от промаха по кнопке, но и праздновать
 * ежедневную мелочь нечего (§3.4).
 *
 * Срабатывает только на переходе «не сделано → сделано»: снятие отметки —
 * исправление ошибки, и отмечать его нечем.
 */
fun Modifier.completionPop(done: Boolean, peak: Float = 1.2f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(done) }

    LaunchedEffect(done) {
        if (done && !previous) {
            scale.snapTo(0.82f)
            scale.animateTo(peak, tween(durationMillis = 110))
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        previous = done
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Нажатие с долгим нажатием и телесным откликом.
 *
 * Обычный combinedClickable ничего не показывает до срабатывания, и строка под
 * пальцем остаётся мёртвой. Здесь у нажатия и у прогиба один источник событий,
 * поэтому отклик приходит сразу, а долгое нажатие остаётся долгим (§9.2).
 */
@Composable
fun Modifier.tappable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    pressedScale: Float = 0.98f,
): Modifier {
    val press = rememberPressSource()
    return this
        .pressScale(press, pressedScale = pressedScale)
        .combinedClickable(
            interactionSource = press,
            indication = ripple(),
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = longClickLabel,
        )
}

/**
 * Нажатие на значок-кнопку: стрелку «назад», шестерёнку, стрелки недели.
 *
 * Они мелкие и попасть по ним не всегда получается с первого раза, поэтому
 * отклик здесь нужнее, чем на крупной кнопке: без него непонятно, засчиталось
 * нажатие или промахнулся. Круг подсветки не обрезается рамкой значка (§9.2).
 */
@Composable
fun Modifier.tapIcon(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val press = rememberPressSource()
    return this
        .pressScale(press, pressedScale = 0.86f)
        .clickable(
            interactionSource = press,
            indication = ripple(bounded = false),
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * Сумма, которая досчитывается до нового значения.
 *
 * Взнос, после которого цифра подменяется мгновенно, читается как опечатка:
 * было одно число, стало другое. Когда сумма доезжает до нового значения,
 * видно, что именно изменилось и на сколько. На первом показе движения нет —
 * досчитывать не от чего.
 */
@Composable
fun animatedMinor(target: Long): Long {
    val animatable = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        animatable.animateTo(target.toFloat(), tween(durationMillis = Motion.CONTENT_MS * 2))
    }
    // По окончании — точная сумма: у Float не хватает знаков на крупные суммы.
    return if (animatable.isRunning) animatable.value.roundToLong() else target
}
