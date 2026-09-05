package eu.bolh.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.ui.common.EmptyState
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.habits.components.HabitRowItem
import eu.bolh.app.ui.habits.components.WeekStrip
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/**
 * Экран привычек — пульт (§9.2): строки с тонкими разделителями, крупные зоны
 * нажатия, недельная полоса сверху.
 *
 * Показываются те привычки, которых в этот день ждут (§5.5). Остальные не
 * пропадают, а лежат под свёрнутой строкой: «не сегодня» — это не «удалено».
 */
@Composable
fun HabitsScreen(
    onCreate: () -> Unit,
    onOpenHabit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    var showOtherDays by remember { mutableStateOf(false) }

    if (state.isEmpty) {
        EmptyState(
            action = stringResource(R.string.habits_empty_action),
            example = stringResource(R.string.habits_empty_example),
            onAction = onCreate,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekHeader(
            title = monthTitle(state),
            onPrevious = viewModel::showPreviousWeek,
            onNext = viewModel::showNextWeek,
            nextEnabled = state.weekDays.lastOrNull()?.isBefore(state.today) == true,
        )
        WeekStrip(
            days = state.weekDays,
            selected = state.selectedDate,
            today = state.today,
            progress = state.weekProgress,
            onSelect = viewModel::selectDate,
        )
        HorizontalDivider(color = status.divider, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Пустой список в день, когда привычек не ждут, читался бы как потеря данных.
            if (state.goodToday.isEmpty() && state.good.isNotEmpty()) {
                item(key = "nothing-today") {
                    Text(
                        text = stringResource(R.string.habits_nothing_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.textTertiary,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.l,
                        ),
                    )
                }
            }

            items(state.goodToday, key = { it.habit.id }) { row ->
                HabitRowItem(
                    row = row,
                    onMark = { level -> viewModel.mark(row.habit, level) },
                    onOpen = { onOpenHabit(row.habit.id) },
                )
                HorizontalDivider(
                    color = status.divider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = Spacing.screenPadding),
                )
            }

            if (state.goodOtherDays.isNotEmpty()) {
                item(key = "other-days") {
                    Text(
                        text = if (showOtherDays) {
                            stringResource(R.string.habits_hide_other)
                        } else {
                            stringResource(R.string.habits_other_days, state.goodOtherDays.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = status.textTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showOtherDays = !showOtherDays }
                            .padding(
                                horizontal = Spacing.screenPadding,
                                vertical = Spacing.l,
                            ),
                    )
                }
                if (showOtherDays) {
                    items(state.goodOtherDays, key = { "other-" + it.habit.id }) { row ->
                        HabitRowItem(
                            row = row,
                            onMark = { level -> viewModel.mark(row.habit, level) },
                            onOpen = { onOpenHabit(row.habit.id) },
                        )
                        HorizontalDivider(
                            color = status.divider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = Spacing.screenPadding),
                        )
                    }
                }
            }

            if (state.bad.isNotEmpty()) {
                item(key = "bad-header") {
                    Text(
                        text = stringResource(R.string.habits_bad_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = status.textTertiary,
                        modifier = Modifier.padding(
                            start = Spacing.screenPadding,
                            top = Spacing.xl,
                            bottom = Spacing.s,
                        ),
                    )
                }
                items(state.bad, key = { it.habit.id }) { row ->
                    HabitRowItem(
                        row = row,
                        onMark = { viewModel.addSlip(row.habit) },
                        onOpen = { onOpenHabit(row.habit.id) },
                        onUndoSlip = { viewModel.undoSlip(row.habit) },
                    )
                    HorizontalDivider(
                        color = status.divider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = Spacing.screenPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenPadding, end = Spacing.m, top = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.habits_week_previous),
                tint = status.textSecondary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrevious)
                    .padding(6.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.habits_week_next),
                tint = if (nextEnabled) status.textSecondary else status.divider,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = nextEnabled, onClick = onNext)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun monthTitle(state: HabitsUiState): String {
    val locale = rememberLocale()
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    val label = state.selectedDate.format(formatter)
    return label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
