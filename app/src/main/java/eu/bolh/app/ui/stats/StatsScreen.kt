package eu.bolh.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.analytics.Insight
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.FinanceBlue
import eu.bolh.app.ui.theme.GoalsTeal
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Статистика (§7). Своего цвета у раздела нет: он не про себя, а про все три,
 * поэтому каждый блок внутри окрашен цветом своего раздела.
 */
@Composable
fun StatsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val locale = rememberLocale()

    if (state.isEmpty) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.stats_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.stats_empty_example),
                style = MaterialTheme.typography.bodyMedium,
                color = status.textSecondary,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.action_settings),
                tint = status.textTertiary,
                modifier = Modifier
                    .padding(top = Spacing.xl)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
    ) {
        item(key = "period") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.m,
                ),
            ) {
                StatPeriod.entries.forEach { period ->
                    Text(
                        text = stringResource(periodLabel(period)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (period == state.period) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            status.textTertiary
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setPeriod(period) }
                            .padding(Spacing.xs),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Настройки живут здесь: статистика — единственный раздел без своих объектов.
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.action_settings),
                    tint = status.textTertiary,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .padding(8.dp),
                )
            }
        }

        item(key = "metrics") {
            Row(
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.l,
                ),
            ) {
                Metric(
                    label = stringResource(R.string.stats_metric_habits),
                    value = state.habitCompletion
                        ?.let { "${(it * 100).roundToInt()}%" }
                        ?: stringResource(R.string.stats_no_value),
                    color = HabitsViolet,
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    label = stringResource(R.string.stats_metric_spend),
                    value = Money.format(state.spendMinor, state.baseCurrency, locale),
                    color = FinanceBlue,
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    label = stringResource(R.string.stats_metric_goals),
                    value = stringResource(
                        R.string.stats_goals_value,
                        state.goalsInTempo,
                        state.goalsCounted,
                    ),
                    color = GoalsTeal,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "habits-block") {
            BlockHeader(stringResource(R.string.stats_block_habits), HabitsViolet)
            if (state.habitRows.isEmpty()) {
                EmptyLine(stringResource(R.string.stats_no_habits))
            } else {
                BarChart(bars = state.bars, color = HabitsViolet)
                state.habitRows.forEach { row ->
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.s,
                        ),
                    ) {
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(
                                R.string.stats_habit_line,
                                row.doneDays,
                                pluralStringResource(
                                    R.plurals.days_of,
                                    row.plannedDays,
                                    row.plannedDays,
                                ),
                                row.minimumDays,
                            ),
                            style = NumberSmall,
                            color = status.textSecondary,
                        )
                    }
                }
            }
        }

        item(key = "goals-block") {
            BlockHeader(stringResource(R.string.stats_block_goals), GoalsTeal)
            if (state.goalRows.isEmpty()) {
                EmptyLine(stringResource(R.string.stats_no_goals))
            } else {
                state.goalRows.forEach { row ->
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.s,
                        ),
                    ) {
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(
                                R.string.stats_goal_line,
                                ((row.progress ?: 0.0) * 100).roundToInt(),
                                row.tempo?.let { "${(it * 100).roundToInt()}%" }
                                    ?: stringResource(R.string.stats_goal_no_tempo),
                            ),
                            style = NumberSmall,
                            color = status.textSecondary,
                        )
                    }
                }
            }
        }

        item(key = "finance-header") {
            BlockHeader(stringResource(R.string.stats_block_finance), FinanceBlue)
            if (state.categories.isEmpty()) {
                EmptyLine(stringResource(R.string.stats_no_spend))
            }
        }

        if (state.categories.isNotEmpty()) {
            items(state.categories.size, key = { "cat-$it" }) { index ->
                val row = state.categories[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.financeRowVertical,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    row.isAnomalous -> status.problem
                                    row.category != null -> Color(row.category.colorArgb)
                                    else -> status.textTertiary
                                },
                            ),
                    )
                    Text(
                        text = categoryName(row.category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.m),
                    )
                    row.delta?.let { delta ->
                        Text(
                            text = (if (delta > 0) "+" else "−") +
                                "${(abs(delta) * 100).roundToInt()}%",
                            style = NumberSmall,
                            color = when {
                                row.isAnomalous -> status.problem
                                delta > 0 -> status.attention
                                else -> status.textTertiary
                            },
                            modifier = Modifier.padding(end = Spacing.m),
                        )
                    }
                    Text(
                        text = Money.format(row.amountMinor, state.baseCurrency, locale),
                        style = MoneyRow,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                HorizontalDivider(
                    color = status.divider,
                    modifier = Modifier.padding(horizontal = Spacing.screenPadding),
                )
            }
        }

        // Блока «Выводы» просто нет, когда сказать нечего. Пустая рамка с надписью
        // «всё хорошо» — это та самая констатация, ради которой перестают заходить.
        if (state.insights.isNotEmpty()) {
            item(key = "insights") {
                Text(
                    text = stringResource(R.string.stats_insights),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(
                        start = Spacing.screenPadding,
                        top = Spacing.xl,
                        bottom = Spacing.s,
                    ),
                )
                state.insights.forEach { insight ->
                    Text(
                        text = insightText(insight, state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs)
                            .clip(RoundedCornerShape(Radius.card))
                            .background(status.surfaceRaised)
                            .padding(Spacing.l),
                    )
                }
            }
        }
    }
}

/** Заголовок блока цветом своего раздела (§7). */
@Composable
private fun BlockHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(
            start = Spacing.screenPadding,
            top = Spacing.xl,
            bottom = Spacing.xs,
        ),
    )
}

/** Честная пустая строка вместо спрятанного блока: раздел есть, данных нет. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalStatusColors.current.textTertiary,
        modifier = Modifier.padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.s,
        ),
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    Column(modifier = modifier.padding(end = Spacing.s)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
        )
        // Сумма вроде «1 227,50 €» в трёх колонках не помещается в одну строку,
        // поэтому ей разрешено переноситься, а не обрезаться многоточием.
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 2,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Столбики темнее в прошлом периоде, ярче ближе к сегодня (§7). */
@Composable
private fun BarChart(bars: List<StatBar>, color: Color) {
    val status = LocalStatusColors.current
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
    ) {
        bars.forEach { bar ->
            val value = (bar.value ?: 0.0).toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((4 + 70 * value).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .alpha(if (bar.isPrevious) 0.35f else 1f)
                    .background(if (bar.value == null) status.surfaceSunken else color),
            )
        }
    }
}

@Composable
private fun insightText(insight: Insight, state: StatsUiState): String {
    val locale = rememberLocale()
    return when (insight) {
    is Insight.CategoryUpWhileHabitSlipping -> stringResource(
        R.string.insight_category_up_habit_slipping,
        categoryName(state.categoriesById[insight.categoryId]),
        (insight.growth * 100).roundToInt(),
        pluralStringResource(R.plurals.days_count, insight.cleanDays, insight.cleanDays),
        insight.totalDays,
    )

    is Insight.GoalStalled -> stringResource(
        R.string.insight_goal_stalled,
        state.goalsById[insight.goalId]?.name.orEmpty(),
        (insight.drop * 100).roundToInt(),
        (insight.tempoNow * 100).roundToInt(),
    )

    is Insight.JarSlowed -> stringResource(
        R.string.insight_jar_slowed,
        state.goalsById[insight.goalId]?.name.orEmpty(),
        insight.monthsShift.toInt(),
    )

    is Insight.DepthLow -> stringResource(
        R.string.insight_depth_low,
        state.habitsById[insight.habitId]?.name.orEmpty(),
        pluralStringResource(R.plurals.days_count, insight.doneDays, insight.doneDays),
        insight.plannedDays,
        insight.minimumDays,
    )

    is Insight.HabitClustered -> stringResource(
        R.string.insight_habit_clustered,
        state.habitsById[insight.habitId]?.name.orEmpty(),
        insight.days.joinToString(", ") {
            it.getDisplayName(TextStyle.FULL, locale)
        },
        pluralStringResource(R.plurals.times_count, insight.plannedPerWeek, insight.plannedPerWeek),
    )

    is Insight.CategoryAnomaly -> stringResource(
        R.string.insight_category_anomaly,
        categoryName(state.categoriesById[insight.categoryId]),
        (insight.growth * 100).roundToInt(),
    )
}
}

@Composable
private fun categoryName(category: Category?): String {
    if (category == null) return stringResource(R.string.finance_uncategorised)
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}

private fun periodLabel(period: StatPeriod): Int = when (period) {
    StatPeriod.Week -> R.string.stats_period_week
    StatPeriod.Month -> R.string.stats_period_month
    StatPeriod.Year -> R.string.stats_period_year
}
