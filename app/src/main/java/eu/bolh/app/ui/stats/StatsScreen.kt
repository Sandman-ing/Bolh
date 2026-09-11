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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.analytics.Insight
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.tapIcon
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ripple
import eu.bolh.app.ui.common.Hint
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.SectionCard
import eu.bolh.app.ui.common.StatTile
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.FinanceBlue
import eu.bolh.app.ui.theme.GoalsTeal
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import eu.bolh.app.ui.theme.TextPrimary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    var blocksMenu by remember { mutableStateOf(false) }
    // Раскрытая привычка и месяц её календаря живут на экране: это способ
    // смотреть, а не данные, и переживать уход отсюда им незачем.
    var openHabit by remember { mutableStateOf<String?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

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
                    .tapIcon(onClick = onOpenSettings)
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
                    val press = rememberPressSource()
                    // Заливка приглушённая, а не белая: у статистики нет
                    // своего цвета, но почти белый чип кричал громче всего
                    // остального на экране и читался как чужой элемент.
                    val fill by animateColorAsState(
                        targetValue = if (selected) {
                            status.textSecondary
                        } else {
                            status.surfaceRaised
                        },
                        animationSpec = Motion.color,
                        label = "period-fill",
                    )
                    val label by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else status.textSecondary,
                        animationSpec = Motion.color,
                        label = "period-label",
                    )
                    Text(
                        text = stringResource(periodLabel(period)),
                        style = MaterialTheme.typography.labelLarge,
                        color = label,
                        modifier = Modifier
                            .pressScale(press)
                            .clip(chip)
                            .background(fill)
                            .border(
                                width = 1.dp,
                                color = if (selected) status.textSecondary else status.stroke,
                                shape = chip,
                            )
                            .clickable(
                                interactionSource = press,
                                indication = ripple(),
                            ) { viewModel.setPeriod(period) }
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Что показывать — решение человека, а не приложения (§7).
                Box {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = stringResource(R.string.stats_blocks),
                        tint = status.textSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(status.surfaceRaised)
                            .border(1.dp, status.stroke, CircleShape)
                            .clickable { blocksMenu = true }
                            .padding(Spacing.s)
                            .size(20.dp),
                    )
                    DropdownMenu(
                        expanded = blocksMenu,
                        onDismissRequest = { blocksMenu = false },
                        containerColor = status.surfaceRaised,
                    ) {
                        StatBlock.entries.forEach { block ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(blockLabel(block)),
                                        color = if (state.shows(block)) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            status.textTertiary
                                        },
                                    )
                                },
                                onClick = { viewModel.toggleBlock(block) },
                            )
                        }
                    }
                }
                // Настройки живут здесь: статистика — единственный раздел без своих объектов.
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.action_settings),
                    tint = status.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(status.surfaceRaised)
                        .border(1.dp, status.stroke, CircleShape)
                        .tapIcon(onClick = onOpenSettings)
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

        if (state.shows(StatBlock.Habits)) item(key = "habits-block") {
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
                    Hint(
                        text = stringResource(R.string.stats_calendar_hint),
                        modifier = Modifier.padding(bottom = Spacing.s),
                    )
                    state.habitRows.forEachIndexed { index, row ->
                        if (index > 0) InnerDivider()
                        val open = openHabit == row.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.block))
                                .clickable {
                                    openHabit = if (open) null else row.id
                                    month = YearMonth.now()
                                }
                                .padding(vertical = Spacing.xs),
                        ) {
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
                        if (open) {
                            HabitCalendar(
                                days = row.days,
                                month = month,
                                onPrevious = { month = month.minusMonths(1) },
                                onNext = { month = month.plusMonths(1) },
                            )
                        }
                    }
                }
            }
        }

        if (state.shows(StatBlock.Goals)) item(key = "goals-block") {
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

        if (state.shows(StatBlock.Money)) item(key = "finance-block") {
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
        if (state.insights.isNotEmpty() && state.shows(StatBlock.Insights)) {
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

/**
 * Месяц привычки: закрашенный день — сделано, полый — минимум, красный — срыв.
 *
 * Строка «14 из 20 дней» говорит, сколько, но не говорит когда. Календарь
 * показывает рисунок: пропуски по выходным видно сразу, а по числу — нет.
 */
@Composable
private fun HabitCalendar(
    days: Map<LocalDate, EntryLevel>,
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val today = LocalDate.now()
    val first = month.atDay(1)
    // Понедельник первым: неделя в приложении начинается с него везде (§5.8).
    val lead = first.dayOfWeek.value - 1
    val cells = lead + month.lengthOfMonth()
    val weeks = (cells + 6) / 7

    Column(modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.s)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = null,
                tint = status.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .tapIcon(onClick = onPrevious)
                    .padding(Spacing.xs)
                    .size(18.dp),
            )
            Text(
                text = month.atDay(1).format(DateTimeFormatter.ofPattern(stringResource(R.string.date_month_year), locale)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = if (month.isBefore(YearMonth.from(today))) {
                    status.textSecondary
                } else {
                    status.divider
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .tapIcon(enabled = month.isBefore(YearMonth.from(today)), onClick = onNext)
                    .padding(Spacing.xs)
                    .size(18.dp),
            )
        }

        Row(modifier = Modifier.padding(top = Spacing.s)) {
            (1..7).forEach { index ->
                Text(
                    text = DayOfWeek.of(index)
                        .getDisplayName(TextStyle.NARROW, locale)
                        .uppercase(locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        repeat(weeks) { week ->
            Row(modifier = Modifier.padding(top = Spacing.xs)) {
                repeat(7) { column ->
                    val dayNumber = week * 7 + column - lead + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Box(modifier = Modifier.weight(1f))
                        return@repeat
                    }
                    val date = month.atDay(dayNumber)
                    val level = days[date]
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when (level) {
                                    EntryLevel.Norm -> HabitsViolet
                                    EntryLevel.Minimum -> HabitsViolet.copy(alpha = 0.35f)
                                    EntryLevel.Slip -> status.problem.copy(alpha = 0.35f)
                                    null -> status.surfaceSunken
                                },
                            )
                            .border(
                                width = if (date == today) 1.5.dp else 0.dp,
                                color = if (date == today) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(10.dp),
                            ),
                    ) {
                        Text(
                            text = dayNumber.toString(),
                            style = NumberSmall,
                            color = when (level) {
                                EntryLevel.Norm -> MaterialTheme.colorScheme.onPrimary
                                null -> status.textTertiary
                                else -> MaterialTheme.colorScheme.onBackground
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun blockLabel(block: StatBlock): Int = when (block) {
    StatBlock.Habits -> R.string.stats_block_habits
    StatBlock.Goals -> R.string.stats_block_goals
    StatBlock.Money -> R.string.stats_block_finance
    StatBlock.Insights -> R.string.stats_insights
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
