package eu.bolh.app.ui.habits.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.habits.DayProgress
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Недельная полоса (§5.8): семь колонок с днём недели и датой.
 *
 * Под датой — точка о том, что в этот день было: закрашенная, если сделано всё
 * запланированное, полая, если начато, и ничего, если в этот день привычек
 * не ждали. Так полоса отвечает на вопрос «где я», а не просто показывает числа.
 */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    progress: Map<LocalDate, DayProgress>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary
    val locale = rememberLocale()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val isToday = day == today
            val isSelected = day == selected
            val isFuture = day.isAfter(today)
            val dayProgress = progress[day]

            val press = rememberPressSource()
            val dayFill by animateColorAsState(
                targetValue = if (isSelected) accent else Color.Transparent,
                animationSpec = Motion.color,
                label = "day-fill",
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pressScale(press, pressedScale = 0.92f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = !isFuture,
                        interactionSource = press,
                        indication = ripple(),
                    ) { onSelect(day) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isFuture -> status.textTertiary
                        isToday -> accent
                        else -> status.textSecondary
                    },
                    textAlign = TextAlign.Center,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        // requiredSize, а не size: в узкой колонке обычный размер
                        // ужимается родителем, и круг дня становится овалом.
                        .requiredSize(30.dp)
                        .clip(CircleShape)
                        .background(dayFill),
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = NumberSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isFuture -> status.textTertiary
                            isToday -> accent
                            else -> MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
                DayDot(
                    progress = dayProgress,
                    accent = accent,
                    idle = status.divider,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DayDot(
    progress: DayProgress?,
    accent: Color,
    idle: Color,
    modifier: Modifier = Modifier,
) {
    val size = 6.dp
    when {
        progress == null || progress.planned == 0 ->
            Box(modifier = modifier.size(size))

        progress.isComplete -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(accent),
        )

        progress.isPartial -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, accent, CircleShape),
        )

        else -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, idle, CircleShape),
        )
    }
}
