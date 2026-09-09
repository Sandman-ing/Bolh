package eu.bolh.app.ui.habits.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.bolh.app.R
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.habits.HabitRowState
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing
import eu.bolh.app.ui.theme.TapTargetMin

/**
 * Строка привычки (§5.8): значок, название, одна строка контекста, одно действие справа.
 * Без карточки — привычки это пульт, а не витрина.
 */
@Composable
fun HabitRowItem(
    row: HabitRowState,
    onMark: (EntryLevel) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onUndoSlip: () -> Unit = {},
) {
    val status = LocalStatusColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.habitRowVertical),
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(BolhIcons.resFor(row.habit.icon)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.m, end = Spacing.m),
        ) {
            Text(
                text = row.habit.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contextLine(row),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    row.isPaused -> status.textTertiary
                    row.needsRecovery -> status.attention
                    else -> status.textSecondary
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        when {
            row.habit.kind == HabitKind.Bad ->
                SlipButton(
                    mode = row.habit.mode,
                    onClick = { onMark(EntryLevel.Slip) },
                    onUndo = onUndoSlip,
                )

            row.habit.mode == HabitMode.Minimum && row.selectedLevel == null ->
                MinimumNormButtons(
                    onMinimum = { onMark(EntryLevel.Minimum) },
                    onNorm = { onMark(EntryLevel.Norm) },
                )

            else -> MarkCircle(
                level = row.selectedLevel,
                onClick = { onMark(EntryLevel.Norm) },
            )
        }
    }

    // Правило вредной привычки написано словами: без него «срыв» — просто
    // кнопка, и непонятно, что она ломает и что считает (§5.4).
    if (row.habit.kind == HabitKind.Bad) {
        Text(
            text = when (row.habit.mode) {
                HabitMode.Limit -> stringResource(
                    R.string.habits_bad_hint_limit,
                    row.habit.limitPerWeek ?: 0,
                )

                HabitMode.Counter -> stringResource(R.string.habits_bad_hint_counter)
                else -> stringResource(R.string.habits_bad_hint_game)
            },
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
            modifier = Modifier.padding(top = Spacing.s),
        )
        Text(
            text = stringResource(R.string.habits_bad_action_hint),
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
        )
    }

    // Отмеченный уровень называется словом: круг с галочкой не отличает
    // минимум от нормы, а разница между ними и есть смысл режима (§5.3).
    if (row.habit.mode == HabitMode.Minimum && row.selectedLevel != null) {
        Text(
            text = stringResource(
                if (row.selectedLevel == EntryLevel.Minimum) {
                    R.string.habit_done_minimum
                } else {
                    R.string.habit_done_norm
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }

    // Что здесь минимум, а что норма, человек писал сам — и должен видеть это
    // под строкой во всю ширину, а не вспоминать при нажатии кнопки (§5.3).
    if (row.habit.mode == HabitMode.Minimum) {
        row.habit.minimumLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = stringResource(R.string.habit_min_line, label),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        row.habit.normLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = stringResource(R.string.habit_norm_line, label),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    }
}

@Composable
private fun contextLine(row: HabitRowState): String {
    val habit = row.habit
    return when {
        // Пауза важнее любой цифры: пока она идёт, считать нечего.
        row.isPaused -> stringResource(R.string.habits_paused)

        row.needsRecovery -> stringResource(R.string.habits_recovery)

        habit.kind == HabitKind.Bad && habit.mode == HabitMode.Limit ->
            stringResource(R.string.habit_slips_of_limit, row.slipsThisWeek, habit.limitPerWeek ?: 0)

        habit.kind == HabitKind.Bad -> {
            val clean = pluralStringResource(
                R.plurals.habit_days_clean,
                row.daysWithoutSlip,
                row.daysWithoutSlip,
            )
            val saved = row.savedMinor?.takeIf { it > 0L }?.let {
                stringResource(
                    R.string.habit_saved,
                    Money.format(it, habit.costCurrencyCode ?: "EUR"),
                )
            }
            if (saved == null) clean else "$clean · $saved"
        }

        row.streak != null && row.streak > 0 ->
            pluralStringResource(R.plurals.habit_streak, row.streak, row.streak)

        else -> stringResource(
            R.string.habit_done_of_planned,
            row.done30,
            pluralStringResource(R.plurals.days_of, row.planned30, row.planned30),
        )
    }
}

/**
 * Круг, а не квадрат: круг читается как действие, квадрат — как ячейка таблицы.
 * Отметка минимума заливается наполовину — видно, что день засчитан, но не полностью.
 */
@Composable
private fun MarkCircle(
    level: EntryLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val status = LocalStatusColors.current
    val filled = level == EntryLevel.Norm

    val press = rememberPressSource()

    // Короткий отклик на отметку: круг проседает и возвращается. Это не
    // празднование, а подтверждение — привычка отмечается каждый день,
    // и длинная анимация здесь быстро надоела бы (§3.4).
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(level) }
    LaunchedEffect(level) {
        if (level != null && previous == null) {
            pop.snapTo(0.72f)
            pop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        previous = level
    }

    val fill by animateColorAsState(
        targetValue = if (filled) accent else Color.Transparent,
        animationSpec = Motion.color,
        label = "mark-fill",
    )
    val ring by animateColorAsState(
        targetValue = if (level != null) accent else status.divider,
        animationSpec = Motion.color,
        label = "mark-ring",
    )

    Box(
        modifier = modifier
            .size(TapTargetMin)
            .pressScale(press, pressedScale = 0.90f)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .background(fill)
                .border(width = 2.dp, color = ring, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (level) {
                EntryLevel.Norm -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.habits_mark_done),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )

                EntryLevel.Minimum -> Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent),
                )

                else -> Unit
            }
        }
    }
}

/**
 * Две кнопки, и обе засчитывают день (§5.3). Плохой день не превращается в ноль,
 * цепочка держится.
 */
@Composable
private fun MinimumNormButtons(
    onMinimum: () -> Unit,
    onNorm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary

    // Обе кнопки нажимаются, поэтому обе выглядят одинаково. Раньше минимум
    // был бледным, и казалось, что кликается только норма.
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Pill(
            text = stringResource(R.string.habits_mark_minimum),
            textColor = accent,
            borderColor = accent,
            onClick = onMinimum,
        )
        Pill(
            text = stringResource(R.string.habits_mark_norm),
            textColor = accent,
            borderColor = accent,
            onClick = onNorm,
        )
    }
}

@Composable
private fun SlipButton(
    mode: HabitMode,
    onClick: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    Pill(
        text = stringResource(
            if (mode == HabitMode.Limit) R.string.habits_slip_add else R.string.habits_slip,
        ),
        textColor = status.problem,
        borderColor = status.problem,
        onClick = onClick,
        modifier = modifier,
        // Нажали не глядя — долгое нажатие возвращает как было.
        onLongClick = onUndo,
        longClickLabel = stringResource(R.string.habits_slip_undo),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pill(
    text: String,
    textColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val press = rememberPressSource()
    val border by animateColorAsState(borderColor, Motion.color, label = "pill-border")

    Box(
        modifier = modifier
            .pressScale(press)
            .clip(RoundedCornerShape(percent = 50))
            .border(1.5.dp, border, RoundedCornerShape(percent = 50))
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(
                        interactionSource = press,
                        indication = ripple(),
                        onClick = onClick,
                    )
                } else {
                    Modifier.combinedClickable(
                        interactionSource = press,
                        indication = ripple(),
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = longClickLabel,
                    )
                },
            )
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}
