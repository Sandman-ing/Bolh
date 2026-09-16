package eu.bolh.app.ui.goals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.bolh.app.R
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.completionPop
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.theme.LocalStatusColors

/** Светлый край заливки: ровная полоса одного цвета читалась как заглушка. */
private fun Color.shine(): Color = lerp(this, Color.White, 0.38f)

/** Больше отрезков в полосе не различить: дальше она рисуется сплошной. */
private const val MAX_SEGMENTS = 12

private const val GLOW_LOW = 0.25f
private const val GLOW_REST = 0.95f

/** Нечётное число: мерцание заканчивается на яркой рамке. */
private const val GLOW_BEATS = 5

/**
 * Путь цели: по отрезку на этап.
 *
 * Сплошная полоса отвечала только «сколько процентов». Отрезки показывают,
 * сколько шагов пройдено и сколько впереди, а текущий этап мерцает: видно,
 * где человек сейчас. Закрытый этап заливается плавно.
 */
@Composable
internal fun StageTrack(
    total: Int,
    done: Int,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val status = LocalStatusColors.current
    val filled by animateFloatAsState(
        targetValue = done.toFloat(),
        animationSpec = tween(Motion.CONTENT_MS * 3),
        label = "stage-track",
    )
    // Текущий этап мерцает несколько раз и затихает: бесконечное движение
    // в списке отвлекает и зря тратит батарею.
    val glow = remember { Animatable(GLOW_REST) }
    LaunchedEffect(done, total) {
        glow.snapTo(GLOW_LOW)
        glow.animateTo(
            targetValue = GLOW_REST,
            animationSpec = repeatable(
                iterations = GLOW_BEATS,
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }
    val track = status.surfaceSunken
    val edge = status.strokeInner
    val light = color.shine()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2f)
        val brush = Brush.horizontalGradient(listOf(color, light), startX = 0f, endX = size.width)

        if (total <= 0 || total > MAX_SEGMENTS) {
            drawRoundRect(color = track, cornerRadius = radius)
            val share = if (total <= 0) 0f else (filled / total).coerceIn(0f, 1f)
            if (share > 0f) {
                drawRoundRect(
                    brush = brush,
                    size = Size(size.width * share, size.height),
                    cornerRadius = radius,
                )
            }
            return@Canvas
        }

        val gap = 4.dp.toPx()
        val width = (size.width - gap * (total - 1)) / total
        for (index in 0 until total) {
            val left = index * (width + gap)
            drawRoundRect(
                color = track,
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
            val share = (filled - index).coerceIn(0f, 1f)
            if (share > 0f) {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, 0f),
                    size = Size(width * share, size.height),
                    cornerRadius = radius,
                )
            } else if (index == done) {
                // Текущий этап: мерцающая рамка вместо заливки.
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = color.copy(alpha = glow.value),
                    topLeft = Offset(left + stroke / 2f, stroke / 2f),
                    size = Size(width - stroke, size.height - stroke),
                    cornerRadius = radius,
                    style = Stroke(width = stroke),
                )
            } else {
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Значок цели в кольце прогресса. Кольцо дорастает до новой доли, а при
 * закрытии этапа значок коротко подпрыгивает: шаг вперёд виден и ощутим.
 */
@Composable
internal fun GoalRing(
    icon: String,
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    iconSize: Dp = 22.dp,
    stepKey: Int = 0,
) {
    val status = LocalStatusColors.current
    val sweep by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(Motion.CONTENT_MS * 3),
        label = "goal-ring",
    )
    val track = status.surfaceSunken
    val light = color.shine()
    val complete = fraction != null && fraction >= 1f
    val fill by animateColorAsState(
        targetValue = if (complete) color else color.copy(alpha = 0.14f),
        animationSpec = Motion.color,
        label = "goal-ring-fill",
    )
    val tint by animateColorAsState(
        targetValue = if (complete) MaterialTheme.colorScheme.onPrimary else color,
        animationSpec = Motion.color,
        label = "goal-ring-tint",
    )

    // Закрыт ещё один этап: значок коротко подпрыгивает. Снятие отметки —
    // исправление, его отмечать нечем.
    val pop = remember { Animatable(1f) }
    var lastStep by remember { mutableIntStateOf(stepKey) }
    LaunchedEffect(stepKey) {
        if (stepKey > lastStep) {
            pop.snapTo(0.84f)
            pop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        lastStep = stepKey
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(size),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width = stroke),
            )
            if (sweep > 0f) {
                rotate(degrees = -90f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to color,
                            sweep to light,
                            1f to light,
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size - 18.dp)
                .clip(CircleShape)
                .background(fill),
        ) {
            Icon(
                painter = painterResource(BolhIcons.resFor(icon)),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Круглая кнопка «этап пройден». После нажатия круг заливается, и только
 * потом строка уступает место следующему этапу.
 */
@Composable
internal fun StageCheckButton(
    checked: Boolean,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val press = rememberPressSource()
    val fill by animateColorAsState(
        targetValue = if (checked) color else Color.Transparent,
        animationSpec = Motion.color,
        label = "stage-check-fill",
    )
    val ring by animateColorAsState(
        targetValue = if (checked) color else color.copy(alpha = 0.7f),
        animationSpec = Motion.color,
        label = "stage-check-ring",
    )
    val tint by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else status.textSecondary,
        animationSpec = Motion.color,
        label = "stage-check-tint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .pressScale(press, pressedScale = 0.88f)
            .clip(CircleShape)
            .clickable(
                interactionSource = press,
                indication = ripple(),
                enabled = !checked,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .completionPop(checked)
                .size(34.dp)
                .clip(CircleShape)
                .background(fill)
                .border(width = 2.dp, color = ring, shape = CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
