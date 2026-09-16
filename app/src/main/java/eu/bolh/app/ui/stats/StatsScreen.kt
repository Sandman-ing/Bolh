package eu.bolh.app.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.analytics.Insight
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.FittedText
import eu.bolh.app.ui.common.Hint
import eu.bolh.app.ui.common.IconTag
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.NoticeBanner
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.common.tapIcon
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.goals.GoalRing
import eu.bolh.app.ui.goals.StageTrack
import eu.bolh.app.ui.goals.TempoTag
import eu.bolh.app.ui.theme.FinanceBlue
import eu.bolh.app.ui.theme.FinanceBlueInk
import eu.bolh.app.ui.theme.GoalsTeal
import eu.bolh.app.ui.theme.GoalsTealInk
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.HabitsVioletInk
import eu.bolh.app.ui.theme.LocalDarkTheme
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.NumberTile
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Цвета разделов: на светлом фоне у каждого свой, более тёмный вариант (§9.3). */
private object SectionColor {
    val habits: Color
        @Composable get() = if (LocalDarkTheme.current) HabitsViolet else HabitsVioletInk
    val goals: Color
        @Composable get() = if (LocalDarkTheme.current) GoalsTeal else GoalsTealInk
    val money: Color
        @Composable get() = if (LocalDarkTheme.current) FinanceBlue else FinanceBlueInk
}

/** Светлый край заливки: столбик одного цвета читался плоско. */
private fun Color.shine(): Color = lerp(this, Color.White, 0.35f)

/**
 * Статистика (§7). Своего цвета у раздела нет: он не про себя, а про все три,
 * поэтому каждый блок внутри окрашен цветом своего раздела.
 */
@Composable
fun StatsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGoal: (String) -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    // Раскрытая привычка и месяц её календаря живут на экране: это способ
    // смотреть, а не данные, и переживать уход отсюда им незачем.
    var openHabit by remember { mutableStateOf<String?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    // Пока данные не пришли, экран пуст: иначе на миг мелькало «привычек
    // пока нет» и нули в плитках.
    if (!state.loaded) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

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

    val blockPadding = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
    ) {
        item(key = "period") {
            PeriodRow(
                state = state,
                onPeriod = viewModel::setPeriod,
                onToggleBlock = viewModel::toggleBlock,
                onOpenSettings = onOpenSettings,
            )
        }

        item(key = "metrics") {
            MetricsRow(state = state)
        }

        if (state.shows(StatBlock.Habits)) {
            item(key = "habits-block") {
                StatSection(
                    title = stringResource(R.string.stats_block_habits),
                    color = SectionColor.habits,
                    icon = R.drawable.ic_tab_habits,
                    modifier = Modifier
                        .animateItem()
                        .then(blockPadding),
                ) {
                    HabitsBlock(
                        state = state,
                        openHabit = openHabit,
                        month = month,
                        onToggleHabit = { id ->
                            openHabit = if (openHabit == id) null else id
                            month = YearMonth.now()
                        },
                        onMonth = { month = it },
                    )
                }
            }
        }

        if (state.shows(StatBlock.Goals)) {
            item(key = "goals-block") {
                StatSection(
                    title = stringResource(R.string.stats_block_goals),
                    color = SectionColor.goals,
                    icon = R.drawable.ic_tab_goals,
                    modifier = Modifier
                        .animateItem()
                        .then(blockPadding),
                    trailing = {
                        if (state.goalRows.isNotEmpty()) {
                            IconTag(
                                text = stringResource(
                                    R.string.stats_goals_in_tempo,
                                    state.goalsInTempo,
                                    state.goalsCounted,
                                ),
                                icon = R.drawable.ic_trending_up,
                                color = SectionColor.goals,
                            )
                        }
                    },
                ) {
                    GoalsBlock(state = state, onOpenGoal = onOpenGoal)
                }
            }
        }

        if (state.shows(StatBlock.Money)) {
            item(key = "finance-block") {
                StatSection(
                    title = stringResource(R.string.stats_block_finance),
                    color = SectionColor.money,
                    icon = R.drawable.ic_tab_finance,
                    modifier = Modifier
                        .animateItem()
                        .then(blockPadding),
                ) {
                    MoneyBlock(state = state)
                }
            }
        }

        // Блока «Выводы» просто нет, когда сказать нечего. Пустая рамка с надписью
        // «всё хорошо» — это та самая констатация, ради которой перестают заходить.
        if (state.insights.isNotEmpty() && state.shows(StatBlock.Insights)) {
            item(key = "insights") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.m, bottom = Spacing.xs),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lightbulb),
                            contentDescription = null,
                            tint = status.attention,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.stats_insights),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    state.insights.forEach { insight ->
                        NoticeBanner(
                            text = insightText(insight, state),
                            color = insightColor(insight),
                            icon = insightIcon(insight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(
    state: StatsUiState,
    onPeriod: (StatPeriod) -> Unit,
    onToggleBlock: (StatBlock) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val status = LocalStatusColors.current
    var blocksMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
    ) {
        // Период — переключатель, а не три слова подряд: у выбранного
        // своя заливка, у остальных обводка, и видно, что это одна группа.
        StatPeriod.entries.forEach { period ->
            val selected = period == state.period
            val chip = RoundedCornerShape(Radius.chip)
            val press = rememberPressSource()
            // Заливка приглушённая, а не белая: у статистики нет своего
            // цвета, и почти белый чип кричал громче всего остального.
            val fill by animateColorAsState(
                targetValue = if (selected) status.textSecondary else status.surfaceRaised,
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
                maxLines = 1,
                modifier = Modifier
                    .pressScale(press)
                    .clip(chip)
                    .background(fill)
                    .border(
                        width = 1.dp,
                        color = if (selected) status.textSecondary else status.stroke,
                        shape = chip,
                    )
                    .clickable(interactionSource = press, indication = ripple()) { onPeriod(period) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // Что показывать — решение человека, а не приложения (§7).
        Box {
            RoundIcon(
                icon = R.drawable.ic_tune,
                description = stringResource(R.string.stats_blocks),
                onClick = { blocksMenu = true },
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
                        leadingIcon = {
                            Icon(
                                painter = painterResource(
                                    if (state.shows(block)) R.drawable.ic_check_circle else R.drawable.ic_close,
                                ),
                                contentDescription = null,
                                tint = if (state.shows(block)) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    status.textTertiary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { onToggleBlock(block) },
                    )
                }
            }
        }
        // Настройки живут здесь: статистика — единственный раздел без своих объектов.
        RoundIcon(
            icon = R.drawable.ic_settings,
            description = stringResource(R.string.action_settings),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, onClick: () -> Unit) {
    val status = LocalStatusColors.current
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = status.textSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(status.surfaceRaised)
            .border(1.dp, status.stroke, CircleShape)
            .tapIcon(onClick = onClick)
            .padding(Spacing.s)
            .size(20.dp),
    )
}

/**
 * Изменение к прошлому периоду: на сколько процентов, в какую сторону и
 * хорошо это или плохо. Знак процента ставит экран: во французском и
 * немецком перед ним пробел.
 */
private data class Delta(val percent: Int, val up: Boolean, val good: Boolean)

private fun percentDelta(now: Long, before: Long): Delta? {
    if (before <= 0L) return null
    val change = ((now - before).toDouble() / before * 100).roundToInt()
    if (change == 0) return null
    return Delta(percent = abs(change), up = change > 0, good = change < 0)
}

private fun pointsDelta(now: Double?, before: Double?): Delta? {
    if (now == null || before == null) return null
    val change = ((now - before) * 100).roundToInt()
    if (change == 0) return null
    return Delta(percent = abs(change), up = change > 0, good = change > 0)
}

/** «+14 %» или «−14 %» по правилам языка. */
@Composable
private fun signedPercent(delta: Delta): String =
    (if (delta.up) "+" else "−") + stringResource(R.string.percent, delta.percent)

@Composable
private fun MetricsRow(state: StatsUiState) {
    val locale = rememberLocale()

    // Высота по самой высокой плитке: подписи разной длины
    // не должны ломать ряд на три разные ступеньки.
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .height(IntrinsicSize.Min),
    ) {
        MetricTile(
            label = stringResource(R.string.stats_metric_habits),
            value = state.habitCompletion
                ?.let { stringResource(R.string.percent, (it * 100).roundToInt()) }
                ?: stringResource(R.string.stats_no_value),
            color = SectionColor.habits,
            icon = R.drawable.ic_tab_habits,
            delta = pointsDelta(state.habitCompletion, state.habitCompletionPrevious),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        MetricTile(
            label = stringResource(R.string.stats_metric_spend),
            value = Money.format(state.spendMinor, state.baseCurrency, locale),
            color = SectionColor.money,
            icon = R.drawable.ic_tab_finance,
            delta = percentDelta(state.spendMinor, state.spendPreviousMinor),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        MetricTile(
            label = stringResource(R.string.stats_metric_goals),
            value = stringResource(R.string.stats_goals_value, state.goalsInTempo, state.goalsCounted),
            color = SectionColor.goals,
            icon = R.drawable.ic_tab_goals,
            delta = null,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/**
 * Плитка показателя: значок раздела, подпись, число и стрелка к прошлому
 * периоду. Одно число без сравнения не говорит, хорошо это или плохо.
 */
@Composable
private fun MetricTile(
    label: String,
    value: String,
    color: Color,
    icon: Int,
    delta: Delta?,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.block)

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .clip(shape)
            .background(status.surfaceRaised)
            .background(color.copy(alpha = 0.07f))
            .border(width = 1.dp, color = color.copy(alpha = 0.35f), shape = shape)
            .padding(Spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = status.textSecondary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Сумма ужимается, а не переносится: «8 000 AMD» в трети экрана
        // ломалось на «8 000 A / MD».
        FittedText(
            text = value,
            style = NumberTile,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (delta != null) {
            val tone = if (delta.good) status.success else status.attention
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (delta.up) R.drawable.ic_trending_up else R.drawable.ic_trending_down,
                    ),
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.percent, delta.percent),
                    style = NumberSmall,
                    color = tone,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            Text(text = " ", style = NumberSmall)
        }
    }
}

/** Блок раздела: значок и название своим цветом, содержимое ниже. */
@Composable
private fun StatSection(
    title: String,
    color: Color,
    icon: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BolhCard(modifier = modifier, contentPadding = PaddingValues(Spacing.l)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.l),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m),
            )
            trailing?.invoke(this)
        }
        content()
    }
}

// --- Привычки ---------------------------------------------------------------

@Composable
private fun HabitsBlock(
    state: StatsUiState,
    openHabit: String?,
    month: YearMonth,
    onToggleHabit: (String) -> Unit,
    onMonth: (YearMonth) -> Unit,
) {
    val status = LocalStatusColors.current
    val color = SectionColor.habits

    if (state.habitRows.isEmpty()) {
        EmptyLine(stringResource(R.string.stats_no_habits))
        return
    }

    HabitSummary(state = state, color = color)

    InnerDivider()
    BarChart(bars = state.bars, period = state.period, color = color)

    if (state.weekdayShares.any { it != null }) {
        InnerDivider()
        WeekdayRhythm(shares = state.weekdayShares, color = color)
    }

    InnerDivider()
    Hint(
        text = stringResource(R.string.stats_calendar_hint),
        modifier = Modifier.padding(bottom = Spacing.m),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        state.habitRows.forEach { row ->
            val open = openHabit == row.id
            val shape = RoundedCornerShape(Radius.control)
            val turn by animateFloatAsState(
                targetValue = if (open) 180f else 0f,
                animationSpec = tween(Motion.CONTENT_MS),
                label = "habit-row-turn",
            )
            val edge by animateColorAsState(
                targetValue = if (open) color.copy(alpha = 0.6f) else status.strokeInner,
                animationSpec = Motion.color,
                label = "habit-row-edge",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(status.surfaceSunken)
                    .border(width = 1.dp, color = edge, shape = shape)
                    .tappable(onClick = { onToggleHabit(row.id) })
                    .padding(Spacing.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.16f)),
                    ) {
                        Icon(
                            painter = painterResource(BolhIcons.resFor(row.icon)),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.m),
                    ) {
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = habitDetail(row),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.percent, (row.share * 100).roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = status.textTertiary,
                        modifier = Modifier
                            .padding(start = Spacing.xs)
                            .size(18.dp)
                            .rotate(turn),
                    )
                }
                GradientLine(
                    fraction = row.share,
                    color = color,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                // Календарь раскрывается, а не возникает: строки под ним уезжают
                // вниз, и видно, откуда он взялся.
                AnimatedVisibility(
                    visible = open,
                    enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
                    exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
                    label = "habit-calendar",
                ) {
                    HabitCalendar(
                        days = row.days,
                        month = month,
                        color = color,
                        onPrevious = { onMonth(month.minusMonths(1)) },
                        onNext = { onMonth(month.plusMonths(1)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun habitDetail(row: StatHabitRow): String {
    val days = pluralStringResource(R.plurals.days_of, row.plannedDays, row.plannedDays)
    return if (row.minimumDays > 0) {
        stringResource(R.string.stats_habit_row, row.doneDays, days, row.minimumDays)
    } else {
        stringResource(R.string.habit_done_of_planned, row.doneDays, days)
    }
}

/** Главное число блока в кольце, рядом сравнение и лучшая серия. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitSummary(state: StatsUiState, color: Color) {
    val status = LocalStatusColors.current
    val completion = state.habitCompletion

    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(
            fraction = (completion ?: 0.0).toFloat(),
            color = color,
            size = 84.dp,
            stroke = 9.dp,
        ) {
            Text(
                text = completion
                    ?.let { stringResource(R.string.percent, (it * 100).roundToInt()) }
                    ?: stringResource(R.string.stats_no_value),
                style = NumberTile,
                color = color,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.l),
        ) {
            Text(
                text = stringResource(doneLabel(state.period)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pointsDelta(completion, state.habitCompletionPrevious)?.let { delta ->
                    IconTag(
                        text = stringResource(
                            R.string.stats_delta_compare,
                            signedPercent(delta),
                            stringResource(compareLabel(state.period)),
                        ),
                        icon = if (delta.up) R.drawable.ic_trending_up else R.drawable.ic_trending_down,
                        color = if (delta.good) status.success else status.attention,
                    )
                }
                state.streakLeader?.let { leader ->
                    IconTag(
                        text = stringResource(
                            R.string.stats_best_streak,
                            leader.name,
                            pluralStringResource(R.plurals.habit_streak, leader.days, leader.days),
                        ),
                        icon = R.drawable.ic_local_fire_department,
                        color = status.attention,
                    )
                }
            }
        }
    }
}

/**
 * Выполнение по отрезкам периода. Столбик нажимается: под графиком
 * появляется его дата и точное число, без угадывания по высоте.
 */
@Composable
private fun BarChart(
    bars: List<StatBar>,
    period: StatPeriod,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    var selected by remember(period, bars.size) { mutableIntStateOf(bars.lastIndex) }
    val gap = if (bars.size > 16) 2.dp else 4.dp
    val previousColor = color.copy(alpha = 0.32f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(chartTitle(period)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            LegendDot(color = previousColor, text = stringResource(R.string.stats_legend_previous))
            LegendDot(
                color = color,
                text = stringResource(R.string.stats_legend_current),
                modifier = Modifier.padding(start = Spacing.s),
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m)
                .height(110.dp),
        ) {
            bars.forEachIndexed { index, bar ->
                val value = (bar.value ?: 0.0).toFloat().coerceIn(0f, 1f)
                val grown by animateFloatAsState(
                    targetValue = value,
                    animationSpec = tween(Motion.CONTENT_MS * 2),
                    label = "bar-$index",
                )
                val isSelected = index == selected
                val shape = RoundedCornerShape(if (bars.size > 16) 3.dp else 6.dp)
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(if (isSelected) color.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { selected = index },
                ) {
                    val brush = when {
                        bar.value == null -> SolidColor(status.surfaceSunken)
                        bar.isPrevious -> SolidColor(previousColor)
                        else -> Brush.verticalGradient(listOf(color.shine(), color))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.04f + 0.96f * grown)
                            .clip(shape)
                            .background(brush)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                shape = shape,
                            ),
                    )
                }
            }
        }

        // Подписи: у года столбиков слишком много, подписан каждый третий.
        val step = if (bars.size > 16) 3 else 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
        ) {
            bars.chunked(step).forEachIndexed { chunkIndex, chunk ->
                val first = chunkIndex * step
                val active = selected in first until first + chunk.size
                Text(
                    text = barLabel(chunk.first().start, period, locale),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = if (active) MaterialTheme.colorScheme.onBackground else status.textTertiary,
                    textAlign = if (step == 1) TextAlign.Center else TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(chunk.size.toFloat()),
                )
            }
        }

        bars.getOrNull(selected)?.let { bar ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = Spacing.m)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(status.surfaceSunken)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Text(
                    text = selectedCaption(bar, period, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = bar.value
                        ?.let { stringResource(R.string.percent, (it * 100).roundToInt()) }
                        ?: stringResource(R.string.stats_no_value),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalStatusColors.current.textTertiary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Какие дни недели удаются, а какие проседают. */
@Composable
private fun WeekdayRhythm(shares: List<Double?>, color: Color) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val best = shares.withIndex()
        .filter { it.value != null }
        .maxByOrNull { it.value ?: 0.0 }
        ?.takeIf { (it.value ?: 0.0) > 0.0 }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_weekdays),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            best?.let {
                IconTag(
                    text = stringResource(
                        R.string.stats_best_weekday,
                        DayOfWeek.of(it.index + 1).getDisplayName(TextStyle.FULL_STANDALONE, locale),
                    ),
                    icon = R.drawable.ic_star,
                    color = color,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m),
        ) {
            shares.forEachIndexed { index, share ->
                val isBest = index == best?.index
                val grown by animateFloatAsState(
                    targetValue = (share ?: 0.0).toFloat().coerceIn(0f, 1f),
                    animationSpec = tween(Motion.CONTENT_MS * 2),
                    label = "weekday-$index",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(status.surfaceSunken),
                    ) {
                        if (share != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.06f + 0.94f * grown)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBest) color else color.copy(alpha = 0.5f)),
                            )
                        }
                    }
                    Text(
                        text = DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, locale)
                            .replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isBest) color else status.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = share
                            ?.let { stringResource(R.string.percent, (it * 100).roundToInt()) }
                            ?: stringResource(R.string.stats_no_value),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = status.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Месяц привычки: закрашенный день — сделано, бледный — минимум, красный — срыв.
 *
 * Строка «14 из 20 дней» говорит, сколько, но не говорит когда. Календарь
 * показывает рисунок: пропуски по выходным видно сразу, а по числу — нет.
 */
@Composable
private fun HabitCalendar(
    days: Map<LocalDate, EntryLevel>,
    month: YearMonth,
    color: Color,
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
    val canNext = month.isBefore(YearMonth.from(today))

    Column(modifier = Modifier.padding(top = Spacing.m)) {
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
                    .padding(Spacing.s)
                    .size(20.dp),
            )
            Text(
                text = first
                    .format(DateTimeFormatter.ofPattern(stringResource(R.string.date_month_year), locale))
                    .replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = if (canNext) status.textSecondary else status.divider,
                modifier = Modifier
                    .clip(CircleShape)
                    .tapIcon(enabled = canNext, onClick = onNext)
                    .padding(Spacing.s)
                    .size(20.dp),
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
                    } else {
                        val date = month.atDay(dayNumber)
                        val level = days[date]
                        val cell = RoundedCornerShape(10.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(cell)
                                .background(
                                    when (level) {
                                        EntryLevel.Norm -> color
                                        EntryLevel.Minimum -> color.copy(alpha = 0.35f)
                                        EntryLevel.Slip -> status.problem.copy(alpha = 0.35f)
                                        null -> status.surfaceRaised
                                    },
                                )
                                .border(
                                    width = if (date == today) 1.5.dp else 0.dp,
                                    color = if (date == today) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = cell,
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

        // Расшифровка цветов: без неё бледная клетка ничего не значит.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.padding(top = Spacing.s),
        ) {
            LegendDot(color = color, text = stringResource(R.string.stats_calendar_norm))
            LegendDot(color = color.copy(alpha = 0.35f), text = stringResource(R.string.stats_calendar_minimum))
            LegendDot(color = status.problem.copy(alpha = 0.5f), text = stringResource(R.string.stats_calendar_slip))
        }
    }
}

// --- Цели -------------------------------------------------------------------

@Composable
private fun GoalsBlock(state: StatsUiState, onOpenGoal: (String) -> Unit) {
    val status = LocalStatusColors.current
    val color = SectionColor.goals

    if (state.goalRows.isEmpty()) {
        EmptyLine(stringResource(R.string.stats_no_goals))
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        state.goalRows.forEach { row ->
            val shape = RoundedCornerShape(Radius.control)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(status.surfaceSunken)
                    .border(width = 1.dp, color = status.strokeInner, shape = shape)
                    .tappable(onClick = { onOpenGoal(row.id) })
                    .padding(Spacing.m),
            ) {
                GoalRing(
                    icon = row.icon,
                    fraction = row.progress?.toFloat(),
                    color = color,
                    size = 52.dp,
                    iconSize = 18.dp,
                    stepKey = row.stagesClosed,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        row.progress?.let { progress ->
                            Text(
                                text = stringResource(R.string.percent, (progress * 100).roundToInt()),
                                style = MaterialTheme.typography.titleMedium,
                                color = color,
                                modifier = Modifier.padding(start = Spacing.s),
                            )
                        }
                    }
                    if (row.stagesTotal > 0) {
                        StageTrack(
                            total = row.stagesTotal,
                            done = row.stagesClosed,
                            color = color,
                            height = 6.dp,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.s),
                    ) {
                        Text(
                            text = if (row.stagesTotal > 0) {
                                stringResource(
                                    R.string.goal_stages_passed,
                                    row.stagesClosed,
                                    pluralStringResource(R.plurals.stages_of, row.stagesTotal, row.stagesTotal),
                                )
                            } else {
                                stringResource(R.string.goal_no_stages)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TempoTag(tempo = row.tempo)
                    }
                }
            }
        }
    }
}

// --- Деньги -----------------------------------------------------------------

@Composable
private fun MoneyBlock(state: StatsUiState) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val color = SectionColor.money

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        FlowTile(
            label = stringResource(R.string.finance_flow_expense),
            amount = Money.format(state.spendMinor, state.baseCurrency, locale),
            color = color,
            icon = R.drawable.ic_trending_down,
            delta = percentDelta(state.spendMinor, state.spendPreviousMinor),
            compare = stringResource(compareLabel(state.period)),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        FlowTile(
            label = stringResource(R.string.finance_flow_income),
            amount = Money.format(state.incomeMinor, state.baseCurrency, locale),
            color = status.success,
            icon = R.drawable.ic_trending_up,
            delta = null,
            compare = null,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }

    if (state.incomeMinor > 0L) {
        val net = state.incomeMinor - state.spendMinor
        Text(
            text = if (net >= 0L) {
                stringResource(R.string.stats_balance_left, Money.format(net, state.baseCurrency, locale))
            } else {
                stringResource(R.string.stats_balance_over, Money.format(-net, state.baseCurrency, locale))
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (net >= 0L) status.success else status.problem,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }

    if (state.categories.isEmpty()) {
        EmptyLine(
            text = stringResource(R.string.stats_no_spend),
            modifier = Modifier.padding(top = Spacing.m),
        )
    } else {
        InnerDivider()
        Text(
            text = stringResource(R.string.stats_spend_structure),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ShareBar(
            state = state,
            modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.s),
        )
        val top = state.categories.maxOf { it.amountMinor }.coerceAtLeast(1L)
        state.categories.forEach { row ->
            CategoryLine(
                row = row,
                total = state.spendMinor,
                top = top,
                currency = state.baseCurrency,
                locale = locale,
            )
        }
    }

    if (state.missingRates.isNotEmpty()) {
        Hint(
            text = stringResource(R.string.stats_missing_rates, state.missingRates.joinToString(", ")),
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

@Composable
private fun FlowTile(
    label: String,
    amount: String,
    color: Color,
    icon: Int,
    delta: Delta?,
    compare: String?,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.control)

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(width = 1.dp, color = color.copy(alpha = 0.35f), shape = shape)
            .padding(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = status.textSecondary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        FittedText(
            text = amount,
            style = NumberTile,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        if (delta != null && compare != null) {
            Text(
                text = stringResource(
                    R.string.stats_delta_compare,
                    signedPercent(delta),
                    compare,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (delta.good) status.success else status.attention,
                maxLines = 2,
            )
        }
    }
}

/** Все траты одной полосой: какая категория занимает сколько места. */
@Composable
private fun ShareBar(state: StatsUiState, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val total = state.categories.sumOf { it.amountMinor }.coerceAtLeast(1L)

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(status.surfaceSunken),
    ) {
        state.categories.forEach { row ->
            val share = row.amountMinor.toFloat() / total
            if (share > 0f) {
                Box(
                    modifier = Modifier
                        .weight(share)
                        .fillMaxHeight()
                        .background(categoryColor(row)),
                )
            }
        }
    }
}

@Composable
private fun CategoryLine(
    row: CategoryStat,
    total: Long,
    top: Long,
    currency: String,
    locale: Locale,
) {
    val status = LocalStatusColors.current
    val tone = categoryColor(row)
    val share = if (total <= 0L) 0 else (row.amountMinor.toDouble() / total * 100).roundToInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.18f)),
        ) {
            Icon(
                painter = painterResource(
                    if (row.isAnomalous) R.drawable.ic_warning else BolhIcons.resFor(row.category?.icon ?: "payments"),
                ),
                contentDescription = null,
                tint = if (row.isAnomalous) status.problem else tone,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = categoryName(row.category),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(R.string.percent, share),
                    style = NumberSmall,
                    color = status.textTertiary,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(status.surfaceSunken),
            ) {
                val grown by animateFloatAsState(
                    targetValue = (row.amountMinor.toFloat() / top).coerceIn(0f, 1f),
                    animationSpec = tween(Motion.CONTENT_MS * 2),
                    label = "category-share",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(grown)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(tone),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.format(row.amountMinor, currency, locale),
                style = MoneyRow,
                color = if (row.isAnomalous) status.problem else MaterialTheme.colorScheme.onBackground,
            )
            categoryDelta(row.delta)?.let { delta ->
                Text(
                    text = signedPercent(delta),
                    style = NumberSmall,
                    color = when {
                        row.isAnomalous -> status.problem
                        delta.up -> status.attention
                        else -> status.success
                    },
                )
            }
        }
    }
}

/** Изменение траты категории; меньше процента не показывается вовсе. */
private fun categoryDelta(delta: Double?): Delta? {
    delta ?: return null
    val rounded = (abs(delta) * 100).roundToInt()
    if (rounded == 0) return null
    return Delta(percent = rounded, up = delta > 0, good = delta < 0)
}

@Composable
private fun categoryColor(row: CategoryStat): Color =
    row.category?.let { Color(it.colorArgb) } ?: LocalStatusColors.current.textTertiary

// --- Общее ------------------------------------------------------------------

/** Кольцо с числом внутри. */
@Composable
private fun ProgressRing(
    fraction: Float,
    color: Color,
    size: Dp,
    stroke: Dp,
    content: @Composable () -> Unit,
) {
    val status = LocalStatusColors.current
    val sweep by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(Motion.CONTENT_MS * 3),
        label = "progress-ring",
    )
    val track = status.surfaceSunken
    val light = color.shine()

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arc = Size(this.size.width - width, this.size.height - width)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width = width),
            )
            if (sweep > 0f) {
                rotate(degrees = -90f) {
                    drawArc(
                        brush = Brush.sweepGradient(0f to color, sweep to light, 1f to light),
                        startAngle = 0f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(width = width, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun GradientLine(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val grown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(Motion.CONTENT_MS * 2),
        label = "gradient-line",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(status.surfaceRaised),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(grown)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(color, color.shine()))),
        )
    }
}

private fun blockLabel(block: StatBlock): Int = when (block) {
    StatBlock.Habits -> R.string.stats_block_habits
    StatBlock.Goals -> R.string.stats_block_goals
    StatBlock.Money -> R.string.stats_block_finance
    StatBlock.Insights -> R.string.stats_insights
}

private fun doneLabel(period: StatPeriod): Int = when (period) {
    StatPeriod.Week -> R.string.stats_done_week
    StatPeriod.Month -> R.string.stats_done_month
    StatPeriod.Year -> R.string.stats_done_year
}

private fun compareLabel(period: StatPeriod): Int = when (period) {
    StatPeriod.Week -> R.string.stats_compare_week
    StatPeriod.Month -> R.string.stats_compare_month
    StatPeriod.Year -> R.string.stats_compare_year
}

private fun chartTitle(period: StatPeriod): Int = when (period) {
    StatPeriod.Week -> R.string.stats_chart_days
    StatPeriod.Month -> R.string.stats_chart_weeks
    StatPeriod.Year -> R.string.stats_chart_months
}

@Composable
private fun barLabel(start: LocalDate, period: StatPeriod, locale: Locale): String = when (period) {
    StatPeriod.Week -> start.dayOfMonth.toString()
    StatPeriod.Month -> start.format(DateTimeFormatter.ofPattern("dd.MM", locale))
    StatPeriod.Year -> start.format(DateTimeFormatter.ofPattern("LLL", locale))
        .trimEnd('.')
        .replaceFirstChar { it.titlecase(locale) }
}

@Composable
private fun selectedCaption(bar: StatBar, period: StatPeriod, locale: Locale): String = when (period) {
    StatPeriod.Week -> bar.start
        .format(DateTimeFormatter.ofPattern(stringResource(R.string.date_weekday_day_month), locale))
        .replaceFirstChar { it.titlecase(locale) }

    StatPeriod.Month -> stringResource(
        R.string.stats_bar_week,
        bar.start.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_day_month), locale)),
    )

    StatPeriod.Year -> bar.start
        .format(DateTimeFormatter.ofPattern(stringResource(R.string.date_month_year), locale))
        .replaceFirstChar { it.titlecase(locale) }
}

/** Честная пустая строка вместо спрятанного блока: раздел есть, данных нет. */
@Composable
private fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalStatusColors.current.textTertiary,
        modifier = modifier,
    )
}

/** Разделитель внутри карточки: тише обводки, иначе рамки спорят. */
@Composable
private fun InnerDivider() {
    HorizontalDivider(
        color = LocalStatusColors.current.divider,
        modifier = Modifier.padding(vertical = Spacing.l),
    )
}

@Composable
private fun insightColor(insight: Insight): Color {
    val status = LocalStatusColors.current
    return when (insight) {
        is Insight.GoalStalled -> status.problem
        is Insight.CategoryAnomaly -> status.problem
        is Insight.CategoryUpWhileHabitSlipping -> status.attention
        is Insight.JarSlowed -> status.attention
        is Insight.DepthLow -> SectionColor.habits
        is Insight.HabitClustered -> SectionColor.habits
    }
}

private fun insightIcon(insight: Insight): Int = when (insight) {
    is Insight.GoalStalled -> R.drawable.ic_trending_down
    is Insight.CategoryAnomaly -> R.drawable.ic_warning
    is Insight.CategoryUpWhileHabitSlipping -> R.drawable.ic_payments
    is Insight.JarSlowed -> R.drawable.ic_savings
    is Insight.DepthLow -> R.drawable.ic_insights
    is Insight.HabitClustered -> R.drawable.ic_calendar_month
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
            // Месяцы отдельным числительным: «уехал на 1 месяцев» — не по-русски.
            pluralStringResource(
                R.plurals.goal_months,
                insight.monthsShift.toInt(),
                insight.monthsShift.toInt(),
            ),
        )

        is Insight.DepthLow -> stringResource(
            R.string.insight_depth_low,
            state.habitsById[insight.habitId]?.name.orEmpty(),
            pluralStringResource(R.plurals.days_count, insight.doneDays, insight.doneDays),
            insight.plannedDays,
            insight.minimumDays,
        )

        // Короткие названия дней не требуют падежа: «в пн, ср» читается
        // одинаково при любом роде привычки.
        is Insight.HabitClustered -> stringResource(
            R.string.insight_habit_clustered,
            state.habitsById[insight.habitId]?.name.orEmpty(),
            insight.days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) },
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
