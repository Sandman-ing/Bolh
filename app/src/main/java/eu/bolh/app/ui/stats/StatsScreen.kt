package eu.bolh.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.SectionCard
import eu.bolh.app.ui.common.StatTile
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.FinanceBlue
import eu.bolh.app.ui.theme.GoalsTeal
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import eu.bolh.app.ui.theme.TextPrimary
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
                // Период — переключатель, а не три слова подряд: у выбранного
                // своя заливка, у остальных обводка, и видно, что это одна группа.
                StatPeriod.entries.forEach { period ->
                    val selected = period == state.period
                    val chip = RoundedCornerShape(Radius.chip)
                    Text(
                        text = stringResource(periodLabel(period)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Ink else status.textSecondary,
                        modifier = Modifier
                            .clip(chip)
                            .background(if (selected) TextPrimary else status.surfaceRaised)
                            .border(
                                width = 1.dp,
                                color = if (selected) TextPrimary else status.stroke,
                                shape = chip,
                            )
                            .clickable { viewModel.setPeriod(period) }
                            .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Настройки живут здесь: статистика — единственный раздел без своих объектов.
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.action_settings),
                    tint = status.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(status.surfaceRaised)
                        .border(1.dp, status.stroke, CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .padding(Spacing.s)
                        .size(20.dp),
                )
            }
        }

        item(key = "metrics") {
            // Высота по самой высокой плитке: подписи разной длины
            // не должны ломать ряд на три разные ступеньки.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier
                    .padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.l,
                    )
                    .height(IntrinsicSize.Min),
            ) {
                StatTile(
                    label = stringResource(R.string.stats_metric_habits),
                    value = state.habitCompletion
                        ?.let { "${(it * 100).roundToInt()}%" }
                        ?: stringResource(R.string.stats_no_value),
                    valueColor = HabitsViolet,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                StatTile(
                    label = stringResource(R.string.stats_metric_spend),
                    value = Money.format(state.spendMinor, state.baseCurrency, locale),
                    valueColor = FinanceBlue,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                StatTile(
                    label = stringResource(R.string.stats_metric_goals),
                    value = stringResource(
                        R.string.stats_goals_value,
                        state.goalsInTempo,
                        state.goalsCounted,
                    ),
                    valueColor = GoalsTeal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        item(key = "habits-block") {
            SectionCard(
                title = stringResource(R.string.stats_block_habits),
                titleColor = HabitsViolet,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
                if (state.habitRows.isEmpty()) {
                    EmptyLine(stringResource(R.string.stats_no_habits))
                } else {
                    BarChart(bars = state.bars, color = HabitsViolet)
                    state.habitRows.forEachIndexed { index, row ->
                        if (index > 0) InnerDivider()
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.titleMedium,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = status.textSecondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }

        item(key = "goals-block") {
            SectionCard(
                title = stringResource(R.string.stats_block_goals),
                titleColor = GoalsTeal,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
                if (state.goalRows.isEmpty()) {
                    EmptyLine(stringResource(R.string.stats_no_goals))
                } else {
                    state.goalRows.forEachIndexed { index, row ->
                        if (index > 0) InnerDivider()
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(
                                R.string.stats_goal_line,
                                ((row.progress ?: 0.0) * 100).roundToInt(),
                                row.tempo?.let {
                                    stringResource(
                                        R.string.stats_goal_tempo,
                                        (it * 100).roundToInt(),
                                    )
                                } ?: stringResource(R.string.stats_goal_no_tempo),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = status.textSecondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }

        item(key = "finance-block") {
            SectionCard(
                title = stringResource(R.string.stats_block_finance),
                titleColor = FinanceBlue,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
                if (state.categories.isEmpty()) {
                    EmptyLine(stringResource(R.string.stats_no_spend))
                }
                state.categories.forEachIndexed { index, row ->
                    if (index > 0) InnerDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
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
                            style = MaterialTheme.typography.bodyLarge,
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
                }
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
                    BolhCard(
                        accent = status.attention,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.xs,
                        ),
                    ) {
                        Text(
                            text = insightText(insight, state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

/** Честная пустая строка вместо спрятанного блока: раздел есть, данных нет. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalStatusColors.current.textTertiary,
    )
}

/** Разделитель внутри карточки: тише обводки, иначе рамки спорят. */
@Composable
private fun InnerDivider() {
    HorizontalDivider(
        color = LocalStatusColors.current.divider,
        modifier = Modifier.padding(vertical = Spacing.m),
    )
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
            .padding(bottom = Spacing.l),
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
