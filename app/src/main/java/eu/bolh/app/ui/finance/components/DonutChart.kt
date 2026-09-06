package eu.bolh.app.ui.finance.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import eu.bolh.app.ui.theme.MoneyHero
import eu.bolh.app.ui.theme.NumberSmall
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Один сектор кольца: доля от целого и цвет. */
data class DonutSlice(val fraction: Float, val color: Color)

/**
 * Кольцевой график расходов (§6.8). Кольцо, а не круг: в центре живёт сумма,
 * и она читается быстрее любой легенды.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerText: String,
    trackColor: Color,
    /** Подпись под главным числом: чем занято само кольцо. */
    centerCaption: String? = null,
    captionColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    diameter: Dp = 190.dp,
    thickness: Dp = 22.dp,
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = Stroke(width = thickness.toPx())
            val inset = thickness.toPx() / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )

            // Зазор между секторами делает соседние категории различимыми
            // без подписей — но только когда сектор достаточно велик.
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                if (sweep > 0f) {
                    val gap = if (sweep > 6f) 1.5f else 0f
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2f,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
                start += sweep
            }
        }
        // Главная цифра живёт внутри кольца: человек ищет «сколько у меня»,
        // и не должен искать это где-то сбоку. Кольцо вокруг — то, куда
        // эти деньги уходят, и связь читается без единой подписи.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                style = MoneyHero,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (centerCaption != null) {
                Text(
                    text = centerCaption,
                    style = NumberSmall,
                    color = captionColor,
                    maxLines = 1,
                )
            }
        }
    }
}
