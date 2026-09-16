package eu.bolh.app.ui.habits.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.bolh.app.R
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.IconTag
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.completionPop
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.habits.HabitRowState
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import eu.bolh.app.ui.theme.TapTargetMin

/**
 * Карточка привычки (§5.8): значок в круге, название, метки, действие.
 *
 * В режиме «минимум и норма» выбор уровня стоит под названием двумя крупными
 * кнопками во всю ширину. Сбоку от длинного названия две мелкие пилюли
 * теснили текст и выглядели приклеенными, а что значит каждый уровень,
 * приходилось искать в справке ниже. Теперь это написано на самой кнопке.
 */
@Composable
fun HabitRowItem(
    row: HabitRowState,
    onMark: (EntryLevel) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onUndoSlip: () -> Unit = {},
) {
    val habit = row.habit
    val status = LocalStatusColors.current
    val bad = habit.kind == HabitKind.Bad
    val tone = if (bad) status.problem else MaterialTheme.colorScheme.primary
    val minimumLabel = habit.minimumLabel?.takeIf { it.isNotBlank() }
    val normLabel = habit.normLabel?.takeIf { it.isNotBlank() }

    val control = when {
        bad -> RowControl.Slip
        // Привычку, которую ждут несколько раз за день, нельзя закрыть
        // выбором уровня: сначала надо набрать все разы (§5.5).
        row.timesPerDay > 1 -> RowControl.Counter
        habit.mode == HabitMode.Minimum && row.selectedLevel == null -> RowControl.Levels
        else -> RowControl.Circle
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .tappable(onClick = onOpen)
            .padding(horizontal = CardPadding, vertical = CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitBadge(
                icon = habit.icon,
                tone = tone,
                done = !bad && row.dayComplete,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m, end = Spacing.s),
            ) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HabitTags(row = row, modifier = Modifier.padding(top = 6.dp))
            }

            // Круг отметки, счётчик и срыв сменяют друг друга переходом. У выбора
            // уровня здесь места нет: его кнопки живут ниже, во всю ширину.
            AnimatedContent(
                targetState = control,
                transitionSpec = {
                    fadeIn(tween(Motion.CONTENT_MS)) togetherWith fadeOut(tween(Motion.CONTENT_MS / 2))
                },
                label = "habit-control",
            ) { shown ->
                when (shown) {
                    RowControl.Slip -> SlipButton(
                        mode = habit.mode,
                        onClick = { onMark(EntryLevel.Slip) },
                        onUndo = onUndoSlip,
                    )

                    RowControl.Counter -> MarkCounter(
                        done = row.marksOnDay,
                        total = row.timesPerDay,
                        onClick = { onMark(EntryLevel.Norm) },
                    )

                    RowControl.Levels -> Unit

                    RowControl.Circle -> MarkCircle(
                        level = row.selectedLevel,
                        onClick = { onMark(EntryLevel.Norm) },
                    )
                }
            }
        }

        // Пропуски на этой неделе: подсказка стоит над кнопками, потому что
        // относится именно к выбору уровня.
        if (row.needsRecovery && !row.isPaused) {
            InlineNotice(
                text = stringResource(R.string.habits_recovery),
                color = status.attention,
                icon = R.drawable.ic_bolt,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }

        // Правило вредной привычки написано словами: без него «срыв» — просто
        // кнопка, и непонятно, что она ломает и что считает (§5.4).
        if (bad) {
            InlineNotice(
                text = when (habit.mode) {
                    HabitMode.Limit -> pluralStringResource(
                        R.plurals.habits_bad_limit,
                        habit.limitPerWeek ?: 0,
                        habit.limitPerWeek ?: 0,
                    )

                    HabitMode.Counter -> stringResource(R.string.habits_bad_hint_counter)
                    else -> stringResource(R.string.habits_bad_hint_game)
                },
                secondary = stringResource(R.string.habits_bad_action_hint),
                color = status.problem,
                icon = R.drawable.ic_shield,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }

        AnimatedVisibility(
            visible = control == RowControl.Levels,
            enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
            exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
            label = "habit-levels",
        ) {
            LevelButtons(
                minimum = minimumLabel,
                norm = normLabel,
                onMinimum = { onMark(EntryLevel.Minimum) },
                onNorm = { onMark(EntryLevel.Norm) },
                modifier = Modifier.padding(top = Spacing.l),
            )
        }

        // Отмеченный уровень называется словом: круг с галочкой не отличает
        // минимум от нормы, а разница между ними и есть смысл режима (§5.3).
        // Уходящая плашка держит прежний уровень: без этого при снятии отметки
        // «минимум» на миг превращался в «норму».
        var lastLevel by remember { mutableStateOf(row.selectedLevel) }
        SideEffect { if (row.selectedLevel != null) lastLevel = row.selectedLevel }
        val shownLevel = row.selectedLevel ?: lastLevel ?: EntryLevel.Norm
        AnimatedVisibility(
            visible = habit.mode == HabitMode.Minimum &&
                row.timesPerDay == 1 &&
                row.selectedLevel != null,
            enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
            exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
            label = "habit-level",
        ) {
            LevelDoneStrip(
                level = shownLevel,
                detail = if (shownLevel == EntryLevel.Minimum) minimumLabel else normLabel,
                // Повторное нажатие того же уровня снимает отметку, и кнопки
                // выбора возвращаются. Пока плашка уходит, снимать уже нечего.
                onChange = { row.selectedLevel?.let(onMark) },
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
    }
}

/** Поля карточки: кнопкам уровня и меткам нужен воздух вокруг. */
private val CardPadding = 18.dp

/**
 * Значок привычки в круге. Когда день закрыт, круг заливается цветом раздела:
 * сделанное видно с первого взгляда на список, ещё до чтения названий.
 */
@Composable
private fun HabitBadge(
    icon: String,
    tone: Color,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(
        targetValue = if (done) tone else tone.copy(alpha = 0.14f),
        animationSpec = Motion.color,
        label = "badge-fill",
    )
    val tint by animateColorAsState(
        targetValue = if (done) MaterialTheme.colorScheme.onPrimary else tone,
        animationSpec = Motion.color,
        label = "badge-tint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .completionPop(done, peak = 1.12f)
            .size(44.dp)
            .clip(CircleShape)
            .background(fill),
    ) {
        Icon(
            painter = painterResource(BolhIcons.resFor(icon)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Метки под названием: время за раз, серия или счёт дней, экономия.
 * У каждой свой значок и цвет, поэтому они читаются, не сливаясь в строку.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitTags(row: HabitRowState, modifier: Modifier = Modifier) {
    val habit = row.habit
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            // Пауза важнее любой цифры: пока она идёт, считать нечего.
            row.isPaused -> IconTag(
                text = stringResource(R.string.habits_paused),
                icon = R.drawable.ic_pause_circle,
                color = status.textSecondary,
            )

            habit.kind == HabitKind.Bad -> {
                if (habit.mode == HabitMode.Limit) {
                    val limit = habit.limitPerWeek ?: 0
                    IconTag(
                        text = stringResource(R.string.habit_slips_of_limit, row.slipsThisWeek, limit),
                        icon = R.drawable.ic_calendar_month,
                        color = when {
                            row.slipsThisWeek > limit -> status.problem
                            row.slipsThisWeek == limit -> status.attention
                            else -> status.success
                        },
                    )
                } else {
                    IconTag(
                        text = pluralStringResource(
                            R.plurals.habit_days_clean,
                            row.daysWithoutSlip,
                            row.daysWithoutSlip,
                        ),
                        icon = R.drawable.ic_shield,
                        color = status.success,
                    )
                }
                row.savedMinor?.takeIf { it > 0L }?.let { saved ->
                    IconTag(
                        text = stringResource(
                            R.string.habit_saved,
                            Money.format(saved, habit.costCurrencyCode ?: "EUR"),
                        ),
                        icon = R.drawable.ic_savings,
                        color = status.success,
                    )
                }
            }

            else -> {
                // Время за раз — на самой карточке, а не только в настройках (§5.8).
                habit.estimatedMinutes?.takeIf { it > 0 }?.let { minutes ->
                    IconTag(
                        text = durationLabel(minutes),
                        icon = R.drawable.ic_timer,
                        color = accent,
                    )
                }
                val streak = row.streak ?: 0
                if (streak > 0) {
                    IconTag(
                        text = pluralStringResource(R.plurals.habit_streak, streak, streak),
                        icon = R.drawable.ic_local_fire_department,
                        color = status.attention,
                    )
                } else {
                    IconTag(
                        text = stringResource(
                            R.string.habit_done_of_planned,
                            row.done30,
                            pluralStringResource(R.plurals.days_of, row.planned30, row.planned30),
                        ),
                        icon = R.drawable.ic_event_available,
                        color = status.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.habit_duration_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.habit_duration_hours, minutes / 60)
    else -> stringResource(R.string.habit_duration_hours_minutes, minutes / 60, minutes % 60)
}

/**
 * Подсказка внутри карточки: цветная подложка и значок в круге.
 * Серую строку мелким шрифтом пролистывали, не читая (§9.3).
 */
@Composable
private fun InlineNotice(
    text: String,
    color: Color,
    icon: Int,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.control)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(width = 1.dp, color = color.copy(alpha = 0.35f), shape = shape)
            .padding(horizontal = Spacing.m, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.m),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textSecondary,
                )
            }
        }
    }
}

/**
 * Две кнопки, и обе засчитывают день (§5.3). Плохой день не превращается
 * в ноль, цепочка держится.
 *
 * Кнопки одной высоты и одной формы: минимум — такой же полноправный выбор,
 * как норма. Норма лишь чуть плотнее залита, как и её отметка в круге.
 */
@Composable
private fun LevelButtons(
    minimum: String?,
    norm: String?,
    onMinimum: () -> Unit,
    onNorm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        LevelButton(
            title = stringResource(R.string.habits_mark_minimum),
            detail = minimum,
            full = false,
            onClick = onMinimum,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        LevelButton(
            title = stringResource(R.string.habits_mark_norm),
            detail = norm,
            full = true,
            onClick = onNorm,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun LevelButton(
    title: String,
    detail: String?,
    full: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPressSource()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(accent.copy(alpha = if (full) 0.20f else 0.08f))
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = if (full) 0.85f else 0.50f),
                shape = shape,
            )
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = Spacing.m, vertical = 10.dp),
    ) {
        LevelMark(full = full, size = 28.dp)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Знак уровня. Повторяет круг отметки справа: точка — минимум, залитый круг
 * с галочкой — норма. Выучив кнопку, человек читает и круг.
 */
@Composable
private fun LevelMark(full: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (full) accent else Color.Transparent)
            .border(width = 2.dp, color = accent, shape = CircleShape),
    ) {
        if (full) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.6f),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/** Засчитанный уровень и его расшифровка; «Изменить» возвращает выбор. */
@Composable
private fun LevelDoneStrip(
    level: EntryLevel,
    detail: String?,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.control)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(width = 1.dp, color = accent.copy(alpha = 0.35f), shape = shape)
            .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
    ) {
        LevelMark(full = level == EntryLevel.Norm, size = 24.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = stringResource(
                    if (level == EntryLevel.Minimum) {
                        R.string.habit_done_minimum
                    } else {
                        R.string.habit_done_norm
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = stringResource(R.string.habit_level_change),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.chip))
                .tappable(onClick = onChange)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}

/**
 * Круг, а не квадрат: круг читается как действие, квадрат — как ячейка таблицы.
 * Отметка минимума — точка внутри: день засчитан, но не полностью.
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
        targetValue = if (level != null) accent else status.stroke,
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
                .size(38.dp)
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
                    modifier = Modifier.size(22.dp),
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
        color = status.problem,
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
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val press = rememberPressSource()
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.5.dp, color, shape)
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
            .heightIn(min = 40.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * Кружок со счётчиком: «1 из 2».
 *
 * Одна галочка на привычку, которую ждут дважды за день, врала бы: день ещё
 * не закрыт. Кольцо по краю показывает, сколько набрано, число внутри — то же
 * словами. Набрал всё — следующее нажатие снимает день целиком (§5.5).
 */
@Composable
private fun MarkCounter(
    done: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val status = LocalStatusColors.current
    val complete = done >= total
    val press = rememberPressSource()

    val share by animateFloatAsState(
        targetValue = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f),
        animationSpec = tween(Motion.CONTENT_MS),
        label = "counter-share",
    )
    val ring by animateColorAsState(
        targetValue = if (done > 0) accent else status.stroke,
        animationSpec = Motion.color,
        label = "counter-ring",
    )
    val fill by animateColorAsState(
        targetValue = if (complete) accent else Color.Transparent,
        animationSpec = Motion.color,
        label = "counter-fill",
    )

    // Озвучка экрана читает «1 из 2», а не «кнопка»: без этого счётчик
    // для незрячего человека не значит ничего.
    val spoken = stringResource(R.string.habit_times_done, done, total)

    Box(
        modifier = modifier
            .size(TapTargetMin)
            .pressScale(press, pressedScale = 0.90f)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            drawArc(
                color = status.divider,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = stroke,
            )
            if (share > 0f) {
                drawArc(
                    color = ring,
                    startAngle = -90f,
                    sweepAngle = 360f * share,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = stroke,
                )
            }
        }
        Box(
            modifier = Modifier
                .completionPop(complete)
                .size(28.dp)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.habits_mark_done),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    text = done.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done > 0) accent else status.textTertiary,
                )
            }
        }
    }
}

/** Что стоит справа в строке привычки. */
private enum class RowControl { Slip, Counter, Levels, Circle }
