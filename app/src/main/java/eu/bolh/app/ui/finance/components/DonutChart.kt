package eu.bolh.app.ui.finance.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import eu.bolh.app.ui.theme.Spacing
import eu.bolh.app.ui.common.FittedText
import eu.bolh.app.ui.theme.MoneyHero
import eu.bolh.app.ui.theme.NumberSmall
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** Ниже этого кегля сумма перестаёт быть заголовком и её незачем ужимать. */
private val MIN_HERO_SIZE = 20.sp

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
            // без подписей — но только когда секторов больше одного. У
            // единственного зазор превращался в серый клин, похожий на сбой.
            val visible = slices.count { it.fraction > 0f }
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                if (sweep > 0f) {
                    val gap = if (visible > 1 && sweep > 6f) 1.5f else 0f
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
            // Сумма ужимается под внутренний круг: «26 850 AMD» длиннее
            // «300 €» и при жёстком кегле переносилась прямо на кольцо.
            FittedText(
                text = centerText,
                style = MoneyHero,
                minFontSize = MIN_HERO_SIZE,
                align = Alignment.Center,
                modifier = Modifier.widthIn(max = diameter - thickness * 2 - Spacing.m * 2),
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
