package eu.bolh.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.common.EmptyState
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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

    if (state.isEmpty) {
        EmptyState(
            action = stringResource(R.string.goals_empty_action),
            example = stringResource(R.string.goals_empty_example),
            onAction = onCreate,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenPadding,
            end = Spacing.screenPadding,
            top = Spacing.m,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.goalCardGap),
    ) {
        item(key = "sort") {
            SortRow(current = state.sort, onSelect = viewModel::setSort)
        }
        items(state.goals, key = { it.goal.id }) { card ->
            GoalCard(card = card, onClick = { onOpenGoal(card.goal.id) })
        }

        if (state.completed.isNotEmpty()) {
            item(key = "completed-header") {
                Text(
                    text = stringResource(R.string.goals_completed),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalStatusColors.current.textTertiary,
                    modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs),
                )
            }
            items(state.completed, key = { "done-" + it.goal.id }) { card ->
                CompletedGoalCard(card = card, onClick = { onOpenGoal(card.goal.id) })
            }
        }
    }
}

/**
 * Порядок целей — редкое решение, поэтому он спрятан под кнопку, а не разложен
 * тремя бледными словами поперёк экрана.
 */
@Composable
private fun SortRow(current: GoalSort, onSelect: (GoalSort) -> Unit) {
    val status = LocalStatusColors.current
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(bottom = Spacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.chip))
                .clickable { open = true }
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.goals_sort_button),
                style = MaterialTheme.typography.labelLarge,
                color = status.textSecondary,
            )
            Text(
                text = " · " + stringResource(sortLabel(current)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
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
private fun CompletedGoalCard(card: CompletedGoalState, onClick: () -> Unit) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(status.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(Spacing.l),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = status.success,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.m),
        ) {
            Text(
                text = card.goal.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = card.goal.doneAt?.let {
                    stringResource(R.string.goal_completed_at, monthYear(it, locale))
                }.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
            )
        }
        if (card.stagesTotal > 0) {
            Text(
                text = stringResource(
                    R.string.goal_completed_stages,
                    pluralStringResource(
                        R.plurals.stages_of,
                        card.stagesClosed,
                        card.stagesClosed,
                    ),
                ),
                style = NumberSmall,
                color = status.success,
            )
        }
    }
}

@Composable
private fun GoalCard(card: GoalCardState, onClick: () -> Unit) {
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary
    val locale = rememberLocale()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(status.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(BolhIcons.resFor(card.goal.icon)),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = card.goal.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m),
            )
            Text(
                text = card.goal.deadline?.let { monthYear(it, locale) }
                    ?: stringResource(R.string.goal_no_deadline),
                style = MaterialTheme.typography.labelLarge,
                // Срок пишется цветом, а не серым: это главное число карточки.
                color = if (card.isOverdue) status.problem else accent,
                maxLines = 1,
            )
        }

        Text(
            text = stringResource(
                R.string.goal_period,
                monthYear(card.goal.startDate, locale),
                card.goal.deadline?.let { monthYear(it, locale) }
                    ?: stringResource(R.string.goal_no_deadline),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        // Цель без этапов показывать нечем — полоса прогресса не рисуется (§4.7).
        if (card.progress != null) {
            ProgressBar(
                fraction = card.progress.toFloat(),
                color = accent,
                track = status.surfaceSunken,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Text(
                text = stringResource(
                    R.string.goal_progress,
                    (card.progress * 100).roundToInt(),
                    card.stagesClosed,
                    pluralStringResource(
                        R.plurals.stages_of,
                        card.stagesTotal,
                        card.stagesTotal,
                    ),
                ),
                style = NumberSmall,
                color = status.textSecondary,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Spacing.s),
        ) {
            Text(
                text = card.tempo?.let {
                    stringResource(R.string.goal_tempo, (it * 100).roundToInt())
                } ?: stringResource(R.string.goal_tempo_unknown),
                style = NumberSmall,
                color = tempoColor(card),
                modifier = Modifier.weight(1f),
            )
            // Значок привязанной привычки — фиолетовый: цвет читается как связь (§9.3).
            if (card.linkedHabits.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    card.linkedHabits.take(4).forEach { habit ->
                        Icon(
                            painter = painterResource(BolhIcons.resFor(habit.icon)),
                            contentDescription = null,
                            tint = HabitsViolet,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        card.nextStage?.let { stage ->
            Text(
                text = stringResource(
                    R.string.goal_next_stage,
                    stage.name,
                    stage.dueDate?.let { monthYear(it, locale) } ?: "",
                ).trimEnd(',', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = status.textSecondary,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}

@Composable
private fun ProgressBar(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/**
 * Темп цветом по значению (§4.7): зелёный — высокий, оранжевый — падает,
 * красный — цель стоит. Статусный цвет перебивает цвет раздела: то, что цель
 * встала, важнее того, откуда она.
 */
@Composable
private fun tempoColor(card: GoalCardState): Color {
    val status = LocalStatusColors.current
    val tempo = card.tempo ?: return status.textTertiary
    return when {
        tempo >= 0.6 -> status.success
        tempo >= 0.3 -> status.attention
        else -> status.problem
    }
}

private fun sortLabel(sort: GoalSort): Int = when (sort) {
    GoalSort.Manual -> R.string.goals_sort_manual
    GoalSort.Deadline -> R.string.goals_sort_deadline
    GoalSort.Tempo -> R.string.goals_sort_tempo
}

internal fun monthYear(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
