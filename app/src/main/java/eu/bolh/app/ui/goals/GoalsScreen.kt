package eu.bolh.app.ui.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.GoalSort
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.Stage
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.CompletionPulse
import eu.bolh.app.ui.common.EmptyState
import eu.bolh.app.ui.common.IconTag
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.completionPop
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.HabitsVioletInk
import eu.bolh.app.ui.theme.LocalDarkTheme
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberTile
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Экран целей — карта пути (§9.2): крупные карточки, много воздуха.
 * Прогресс и темп стоят рядом, но никогда не складываются в одно число (§4.2).
 */
@Composable
fun GoalsScreen(
    onCreate: () -> Unit,
    onOpenGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var celebrating by remember { mutableStateOf(false) }

    // Этап, только что закрытый с карточки. Несколько секунд его можно
    // вернуть: промахнуться по круглой кнопке в списке нетрудно.
    var justClosed by remember { mutableStateOf<Stage?>(null) }
    LaunchedEffect(justClosed) {
        if (justClosed != null) {
            delay(UNDO_WINDOW_MS)
            justClosed = null
        }
    }

    // Пока цели не прочитаны, экран пуст: иначе на миг оставалась одна
    // кнопка сортировки, а карточки появлялись следом.
    if (!state.loaded) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    if (state.isEmpty) {
        EmptyState(
            action = stringResource(R.string.goals_empty_action),
            example = stringResource(R.string.goals_empty_example),
            onAction = onCreate,
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.l,
                end = Spacing.l,
                top = Spacing.m,
                bottom = Spacing.fabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.goalCardGap),
        ) {
            item(key = "sort") {
                SortRow(current = state.sort, onSelect = viewModel::setSort)
            }
            items(state.goals, key = { it.goal.id }) { card ->
                GoalCard(
                    card = card,
                    justClosed = justClosed?.takeIf { it.goalId == card.goal.id },
                    onClick = { onOpenGoal(card.goal.id) },
                    onStageDone = { stage ->
                        viewModel.setStageDone(stage, done = true)
                        justClosed = stage
                    },
                    onUndoStage = { stage ->
                        viewModel.setStageDone(stage, done = false)
                        justClosed = null
                    },
                    onCompleteGoal = {
                        viewModel.completeGoal(card.goal.id)
                        celebrating = true
                    },
                    modifier = Modifier.animateItem(),
                )
            }

            if (state.completed.isNotEmpty()) {
                item(key = "completed-header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier
                            .animateItem()
                            .padding(start = Spacing.xs, top = Spacing.l),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_emoji_events),
                            contentDescription = null,
                            tint = LocalStatusColors.current.success,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.goals_completed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                items(state.completed, key = { "done-" + it.goal.id }) { card ->
                    CompletedGoalCard(
                        card = card,
                        onClick = { onOpenGoal(card.goal.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (celebrating) {
            CompletionPulse(
                accent = MaterialTheme.colorScheme.primary,
                onFinished = { celebrating = false },
            )
        }
    }
}

private const val UNDO_WINDOW_MS = 6_000L

/**
 * Порядок целей — редкое решение, поэтому он спрятан под кнопку, а не разложен
 * тремя бледными словами поперёк экрана.
 */
@Composable
private fun SortRow(current: GoalSort, onSelect: (GoalSort) -> Unit) {
    val status = LocalStatusColors.current
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(bottom = Spacing.xs)) {
        val shape = RoundedCornerShape(Radius.chip)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(status.surfaceRaised)
                .border(1.dp, status.stroke, shape)
                .tappable(onClick = { open = true })
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.goals_sort_button),
                style = MaterialTheme.typography.labelMedium,
                color = status.textSecondary,
            )
            Text(
                text = "  " + stringResource(sortLabel(current)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Spacing.xs)
                    .size(14.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = status.surfaceRaised,
        ) {
            GoalSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(sortLabel(sort)),
                            color = if (sort == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    },
                    onClick = {
                        onSelect(sort)
                        open = false
                    },
                )
            }
        }
    }
}

/** Достигнутая цель: без полосы прогресса и темпа, только итог. */
@Composable
private fun CompletedGoalCard(
    card: CompletedGoalState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()

    BolhCard(
        onClick = onClick,
        accent = status.success,
        contentPadding = PaddingValues(Spacing.l),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(status.success.copy(alpha = 0.18f)),
            ) {
                Icon(
                    painter = painterResource(BolhIcons.resFor(card.goal.icon)),
                    contentDescription = null,
                    tint = status.success,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m, end = Spacing.s),
            ) {
                Text(
                    text = card.goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.goal.doneAt?.let {
                        stringResource(R.string.goal_completed_at, monthYear(it, locale))
                    }.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textSecondary,
                )
            }
            if (card.stagesTotal > 0) {
                IconTag(
                    text = pluralStringResource(
                        R.plurals.stages_count,
                        card.stagesClosed,
                        card.stagesClosed,
                    ),
                    icon = R.drawable.ic_check,
                    color = status.success,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    card: GoalCardState,
    justClosed: Stage?,
    onClick: () -> Unit,
    onStageDone: (Stage) -> Unit,
    onUndoStage: (Stage) -> Unit,
    onCompleteGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    // Цель с прошедшим сроком обведена красным: статус перебивает цвет
    // раздела, потому что «встала» важнее, чем «это цель» (§9.3).
    val tone = if (card.isOverdue) status.problem else MaterialTheme.colorScheme.primary

    BolhCard(
        onClick = onClick,
        modifier = modifier,
        accent = if (card.isOverdue) status.problem else null,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(
                icon = card.goal.icon,
                fraction = card.progress?.toFloat(),
                color = tone,
                stepKey = card.stagesClosed,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = card.goal.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    // Срок пишется цветом, а не серым: это главное число карточки.
                    IconTag(
                        text = card.goal.deadline?.let { monthYear(it, locale) }
                            ?: stringResource(R.string.goal_no_deadline),
                        icon = R.drawable.ic_calendar_month,
                        color = tone,
                    )
                    DaysLeftTag(daysLeft = card.daysLeft)
                    TempoTag(tempo = card.tempo)
                }
            }
        }

        // Цель без этапов показывать нечем — путь не рисуется (§4.7).
        if (card.stagesTotal > 0) {
            StageTrack(
                total = card.stagesTotal,
                done = card.stagesClosed,
                color = tone,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (card.stagesTotal > 0) Spacing.s else Spacing.m),
        ) {
            Text(
                text = if (card.stagesTotal > 0) {
                    stringResource(
                        R.string.goal_stages_passed,
                        card.stagesClosed,
                        pluralStringResource(
                            R.plurals.stages_of,
                            card.stagesTotal,
                            card.stagesTotal,
                        ),
                    )
                } else {
                    stringResource(R.string.goal_no_stages)
                },
                style = MaterialTheme.typography.labelMedium,
                color = status.textSecondary,
                modifier = Modifier.weight(1f),
            )
            // Доля стоит под путём, а не рядом с названием: там она отнимала
            // место, и длинные названия обрезались.
            card.progress?.let { progress ->
                Text(
                    text = stringResource(R.string.percent, (progress * 100).roundToInt()),
                    style = NumberTile,
                    color = tone,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }

        val slot = when {
            justClosed != null -> StageSlot.Closed(justClosed)
            card.nextStage != null -> StageSlot.Next(card.nextStage, card.nextStageNumber)
            card.stagesTotal > 0 -> StageSlot.AllDone
            else -> StageSlot.None
        }
        AnimatedContent(
            targetState = slot,
            contentKey = { it.key },
            transitionSpec = {
                (fadeIn(tween(Motion.CONTENT_MS)) + slideInVertically(tween(Motion.CONTENT_MS)) { it / 3 }) togetherWith
                    fadeOut(tween(Motion.CONTENT_MS / 2)) using SizeTransform(clip = false)
            },
            label = "goal-stage",
        ) { shown ->
            when (shown) {
                is StageSlot.Next -> NextStageRow(
                    stage = shown.stage,
                    number = shown.number,
                    total = card.stagesTotal,
                    color = tone,
                    onDone = { onStageDone(shown.stage) },
                    modifier = Modifier.padding(top = Spacing.l),
                )

                is StageSlot.Closed -> ClosedStageRow(
                    stage = shown.stage,
                    onUndo = { onUndoStage(shown.stage) },
                    modifier = Modifier.padding(top = Spacing.l),
                )

                StageSlot.AllDone -> AllStagesDoneRow(
                    onComplete = onCompleteGoal,
                    modifier = Modifier.padding(top = Spacing.l),
                )

                StageSlot.None -> Box(modifier = Modifier.fillMaxWidth())
            }
        }

        if (card.linkedHabits.isNotEmpty()) {
            LinkedHabitsRow(
                habits = card.linkedHabits,
                doneToday = card.habitsDoneToday,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}

/** Что стоит под путём цели: следующий этап, только что закрытый или итог. */
private sealed interface StageSlot {
    val key: String

    data class Next(val stage: Stage, val number: Int) : StageSlot {
        override val key: String get() = "next-" + stage.id
    }

    data class Closed(val stage: Stage) : StageSlot {
        override val key: String get() = "closed-" + stage.id
    }

    data object AllDone : StageSlot {
        override val key: String get() = "all-done"
    }

    data object None : StageSlot {
        override val key: String get() = "none"
    }
}

@Composable
private fun DaysLeftTag(daysLeft: Long?) {
    if (daysLeft == null) return
    val status = LocalStatusColors.current
    val days = daysLeft.coerceIn(-9_999L, 9_999L).toInt()

    when {
        days < 0 -> IconTag(
            text = pluralStringResource(R.plurals.goal_days_overdue, -days, -days),
            icon = R.drawable.ic_event_busy,
            color = status.problem,
        )

        days == 0 -> IconTag(
            text = stringResource(R.string.goal_due_today),
            icon = R.drawable.ic_schedule,
            color = status.attention,
        )

        else -> IconTag(
            text = pluralStringResource(R.plurals.goal_days_left, days, days),
            icon = R.drawable.ic_schedule,
            color = if (days <= 7) status.attention else status.textSecondary,
        )
    }
}

/**
 * Темп цветом по значению (§4.7): зелёный — высокий, оранжевый — падает,
 * красный — цель стоит. Статусный цвет перебивает цвет раздела: то, что цель
 * встала, важнее того, откуда она.
 */
@Composable
internal fun TempoTag(tempo: Double?) {
    val status = LocalStatusColors.current
    val color = when {
        tempo == null -> status.textTertiary
        tempo >= 0.6 -> status.success
        tempo >= 0.3 -> status.attention
        else -> status.problem
    }
    IconTag(
        text = tempo?.let { stringResource(R.string.goal_tempo, (it * 100).roundToInt()) }
            ?: stringResource(R.string.goal_tempo_unknown),
        icon = if (tempo != null && tempo < 0.3) R.drawable.ic_trending_down else R.drawable.ic_trending_up,
        color = color,
    )
}

/** Следующий этап и кнопка, которая закрывает его прямо отсюда. */
@Composable
private fun NextStageRow(
    stage: Stage,
    number: Int,
    total: Int,
    color: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val shape = RoundedCornerShape(Radius.control)
    val today = remember { LocalDate.now() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(status.surfaceSunken)
            .border(width = 1.dp, color = status.strokeInner, shape = shape)
            .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flag),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.m, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.goal_stage_number, number, total),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
            Text(
                text = stage.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            stage.dueDate?.let { due ->
                Text(
                    text = stringResource(
                        R.string.goal_stage_due,
                        due.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_day_month), locale)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (due.isBefore(today)) status.problem else status.textSecondary,
                )
            }
        }
        StageCheckButton(
            checked = false,
            color = color,
            contentDescription = stringResource(R.string.goal_stage_done_action),
            onClick = onDone,
        )
    }
}

/** Этап закрыт: короткое подтверждение и возможность вернуть. */
@Composable
private fun ClosedStageRow(
    stage: Stage,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.control)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(status.success.copy(alpha = 0.12f))
            .border(width = 1.dp, color = status.success.copy(alpha = 0.45f), shape = shape)
            .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .completionPop(shown)
                .size(34.dp)
                .clip(CircleShape)
                .background(status.success),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.m),
        ) {
            Text(
                text = stringResource(R.string.goal_stage_closed),
                style = MaterialTheme.typography.labelMedium,
                color = status.success,
            )
            Text(
                text = stage.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.goal_stage_undo),
            style = MaterialTheme.typography.labelLarge,
            color = status.textSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.chip))
                .tappable(onClick = onUndo)
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
        )
    }
}

/** Все этапы пройдены: остаётся признать цель достигнутой. */
@Composable
private fun AllStagesDoneRow(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.control)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(status.success.copy(alpha = 0.12f))
            .border(width = 1.dp, color = status.success.copy(alpha = 0.45f), shape = shape)
            .padding(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(status.success.copy(alpha = 0.22f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_emoji_events),
                    contentDescription = null,
                    tint = status.success,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(R.string.goal_all_stages_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
        PillButton(
            text = stringResource(R.string.goal_complete),
            onClick = onComplete,
            icon = R.drawable.ic_check,
            color = status.success,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

/**
 * Привычки, которые ведут к цели. Закрытая сегодня залита своим цветом:
 * видно, что уже сделано для цели за день. Фиолетовый читается как связь
 * с разделом привычек (§9.3).
 */
@Composable
private fun LinkedHabitsRow(
    habits: List<Habit>,
    doneToday: Set<String>,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val violet = if (LocalDarkTheme.current) HabitsViolet else HabitsVioletInk

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            habits.take(MAX_HABIT_ICONS).forEach { habit ->
                val done = habit.id in doneToday
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (done) violet else violet.copy(alpha = 0.16f)),
                ) {
                    Icon(
                        painter = painterResource(BolhIcons.resFor(habit.icon)),
                        contentDescription = null,
                        tint = if (done) MaterialTheme.colorScheme.onPrimary else violet,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.goal_habits_today, doneToday.size, habits.size),
            style = MaterialTheme.typography.labelMedium,
            color = status.textSecondary,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

private const val MAX_HABIT_ICONS = 5

private fun sortLabel(sort: GoalSort): Int = when (sort) {
    GoalSort.Manual -> R.string.goals_sort_manual
    GoalSort.Deadline -> R.string.goals_sort_deadline
    GoalSort.Tempo -> R.string.goals_sort_tempo
}

/** Шаблон даты живёт в ресурсах: у каждого языка свой порядок частей (§9.6). */
@Composable
internal fun monthYear(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_month_year), locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
