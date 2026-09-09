package eu.bolh.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.prefs.SettingsStore
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.analytics.CategoryFacts
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.analytics.GoalAnalytics
import eu.bolh.app.domain.analytics.GoalFacts
import eu.bolh.app.domain.analytics.HabitAnalytics
import eu.bolh.app.domain.analytics.HabitFacts
import eu.bolh.app.domain.analytics.Insight
import eu.bolh.app.domain.analytics.InsightInput
import eu.bolh.app.domain.analytics.Insights
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.Mark
import eu.bolh.app.domain.model.Stage
import eu.bolh.app.domain.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Периоды статистики (§7). По умолчанию — месяц. */
enum class StatPeriod { Week, Month, Year }

/** Столбик графика. Прошлый период рисуется тусклее, ближе к сегодня — ярче. */
data class StatBar(val label: String, val value: Double?, val isPrevious: Boolean)

data class CategoryStat(
    val category: Category?,
    val amountMinor: Long,
    val delta: Double?,
    val isAnomalous: Boolean,
)

/** Строка привычки в статистике: постоянство и глубина рядом (§5.3). */
/** Блок статистики, который можно убрать с экрана (§7). */
enum class StatBlock { Habits, Goals, Money, Insights }

data class StatHabitRow(
    val id: String,
    val name: String,
    val doneDays: Int,
    val plannedDays: Int,
    val minimumDays: Int,
    /** Отметки по дням — из них рисуется календарь привычки. */
    val days: Map<LocalDate, EntryLevel> = emptyMap(),
)

/** Строка цели: прогресс и темп — два разных числа, и они не складываются (§4.2). */
data class StatGoalRow(
    val name: String,
    val progress: Double?,
    val tempo: Double?,
)

data class StatsUiState(
    val period: StatPeriod = StatPeriod.Month,
    val habitCompletion: Double? = null,
    val spendMinor: Long = 0L,
    val spendPreviousMinor: Long = 0L,
    val goalsInTempo: Int = 0,
    val goalsCounted: Int = 0,
    val habitRows: List<StatHabitRow> = emptyList(),
    val goalRows: List<StatGoalRow> = emptyList(),
    val bars: List<StatBar> = emptyList(),
    val categories: List<CategoryStat> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val habitsById: Map<String, Habit> = emptyMap(),
    val goalsById: Map<String, Goal> = emptyMap(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val baseCurrency: String = "EUR",
    /** Пусто — показываем всё. */
    val hiddenBlocks: Set<StatBlock> = emptySet(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean
        get() = loaded && habitRows.isEmpty() && goalRows.isEmpty() && spendMinor == 0L

    fun shows(block: StatBlock): Boolean = block !in hiddenBlocks
}

class StatsViewModel(
    private val habits: HabitRepository,
    private val goals: GoalRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val period = MutableStateFlow(StatPeriod.Month)

    init {
        // Период запоминается: человек выбрал год — значит, год и должен открываться.
        viewModelScope.launch {
            settings.statPeriod.collect { stored ->
                period.value = StatPeriod.entries
                    .firstOrNull { it.name.equals(stored, ignoreCase = true) }
                    ?: StatPeriod.Month
            }
        }
    }

    private val habitData = combine(
        habits.observeAllHabits(),
        habits.observeMarks(from = habits.today().minusDays(760), to = habits.today()),
    ) { habitList, marks -> habitList to marks }

    private val goalData = combine(
        goals.observeActiveGoals(),
        goals.observeStages(),
    ) { goalList, stages -> goalList to stages }

    private val dataFlow = combine(
        habitData,
        goalData,
        finance.observeTransactions(),
        finance.observeCategories(),
    ) { habitPair, goalPair, transactions, categories ->
        Data(
            habits = habitPair.first,
            marks = habitPair.second,
            goals = goalPair.first,
            stages = goalPair.second,
            transactions = transactions,
            categories = categories,
        )
    }

    private val hidden = settings.statBlocks.map { stored ->
        stored.mapNotNull { name -> runCatching { StatBlock.valueOf(name) }.getOrNull() }.toSet()
    }

    val state: StateFlow<StatsUiState> = combine(
        dataFlow,
        period,
        hidden,
        finance.currency,
    ) { data, currentPeriod, hiddenBlocks, currency ->
        val today = habits.today()
        val current = boundsFor(currentPeriod, today)
        val previous = previousBounds(currentPeriod, current)

        val goodHabits = data.habits.filter { it.kind == HabitKind.Good }
        val completion = averageTempo(goodHabits, data.marks, current)

        val categoriesById = data.categories.associateBy { it.id }
        val spendNow = FinanceAnalytics.spendByCategory(data.transactions, current.from, current.to)
        val spendBefore = FinanceAnalytics.spendByCategory(data.transactions, previous.from, previous.to)

        val goalTempos = data.goals.associate { goal ->
            val linked = data.habits.filter { it.goalId == goal.id }
            goal.id to GoalAnalytics.tempo(
                linked.mapNotNull { habit ->
                    HabitAnalytics.tempo(
                        marks = data.marks[habit.id].orEmpty(),
                        schedule = habit.schedule,
                        window = current,
                        startDate = habit.startDate,
                    )
                },
            )
        }

        StatsUiState(
            period = currentPeriod,
            habitCompletion = completion,
            spendMinor = spendNow.values.sum(),
            spendPreviousMinor = spendBefore.values.sum(),
            // «N из M целей в темпе»: в знаменателе все цели, а не только те,
            // у которых есть привычки. Иначе «0 из 0» выглядит как поломка.
            goalsInTempo = goalTempos.values.count { it != null && it >= IN_TEMPO },
            goalsCounted = data.goals.size,
            habitRows = goodHabits.map { habit ->
                val marks = data.marks[habit.id].orEmpty().filter { current.covers(it.date) }
                StatHabitRow(
                    id = habit.id,
                    name = habit.name,
                    doneDays = HabitAnalytics.doneDays(
                        data.marks[habit.id].orEmpty(),
                        current,
                        habit.startDate,
                    ),
                    plannedDays = HabitAnalytics.plannedDaysRounded(
                        habit.schedule,
                        current,
                        habit.startDate,
                    ),
                    minimumDays = marks.count { it.level == EntryLevel.Minimum },
                    // Календарь показывает весь известный след привычки,
                    // а не только выбранный период: месяц листается сам по себе.
                    days = data.marks[habit.id].orEmpty().associate { it.date to it.level },
                )
            },
            goalRows = data.goals.map { goal ->
                val stages = data.stages[goal.id].orEmpty()
                StatGoalRow(
                    name = goal.name,
                    progress = GoalAnalytics.progress(stages.size, stages.count { it.isDone }),
                    tempo = goalTempos[goal.id],
                )
            },
            bars = barsFor(currentPeriod, today, goodHabits, data.marks),
            categories = spendNow.entries
                .sortedByDescending { it.value }
                .map { (categoryId, amount) ->
                    CategoryStat(
                        category = categoriesById[categoryId],
                        amountMinor = amount,
                        delta = FinanceAnalytics.delta(amount, spendBefore[categoryId] ?: 0L),
                        isAnomalous = FinanceAnalytics.isAnomalous(
                            currentMinor = amount,
                            historyMonthly = monthlyHistory(categoryId, data.transactions, today),
                        ),
                    )
                },
            insights = Insights.of(
                InsightInput(
                    habits = habitFacts(data, current),
                    goals = data.goals.map { goal ->
                        GoalFacts(
                            goalId = goal.id,
                            tempoNow = goalTempos[goal.id],
                            tempoPrevious = GoalAnalytics.tempo(
                                data.habits.filter { it.goalId == goal.id }.mapNotNull { habit ->
                                    HabitAnalytics.tempo(
                                        marks = data.marks[habit.id].orEmpty(),
                                        schedule = habit.schedule,
                                        window = previous,
                                        startDate = habit.startDate,
                                    )
                                },
                            ),
                        )
                    },
                    categories = spendNow.keys.mapNotNull { categoryId ->
                        categoryId ?: return@mapNotNull null
                        CategoryFacts(
                            categoryId = categoryId,
                            spentNow = spendNow[categoryId] ?: 0L,
                            spentPrevious = spendBefore[categoryId] ?: 0L,
                            historyMonthly = monthlyHistory(categoryId, data.transactions, today),
                            linkedHabitId = data.habits
                                .firstOrNull { it.categoryId == categoryId && it.kind == HabitKind.Bad }
                                ?.id,
                        )
                    },
                ),
            ),
            habitsById = data.habits.associateBy { it.id },
            goalsById = data.goals.associateBy { it.id },
            categoriesById = categoriesById,
            baseCurrency = currency,
            hiddenBlocks = hiddenBlocks,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun setPeriod(value: StatPeriod) {
        period.value = value
        viewModelScope.launch { settings.setStatPeriod(value.name.lowercase()) }
    }

    /** Блок либо есть на экране, либо его нет: третьего состояния не нужно. */
    fun toggleBlock(block: StatBlock) {
        viewModelScope.launch {
            val current = settings.statBlocks.first()
            val next = if (block.name in current) current - block.name else current + block.name
            settings.setStatBlocks(next)
        }
    }

    private fun averageTempo(
        habitList: List<Habit>,
        marks: Map<String, List<Mark>>,
        window: DayWindow,
    ): Double? {
        val tempos = habitList.mapNotNull { habit ->
            HabitAnalytics.tempo(
                marks = marks[habit.id].orEmpty(),
                schedule = habit.schedule,
                window = window,
                startDate = habit.startDate,
            )
        }
        return if (tempos.isEmpty()) null else tempos.average()
    }

    private fun habitFacts(data: Data, window: DayWindow): List<HabitFacts> =
        data.habits.map { habit ->
            val marks = data.marks[habit.id].orEmpty()
            val inWindow = marks.filter { window.covers(it.date) }
            val done = inWindow.filter { it.level != EntryLevel.Slip }.map { it.date }.distinct()
            val cleanDays = if (habit.kind == HabitKind.Bad) {
                window.dates().count { day -> inWindow.none { it.date == day && it.level == EntryLevel.Slip } }
            } else {
                done.size
            }
            HabitFacts(
                habitId = habit.id,
                kind = habit.kind,
                doneDays = cleanDays,
                plannedDays = if (habit.kind == HabitKind.Bad) {
                    window.dates().size
                } else {
                    HabitAnalytics.plannedDaysRounded(habit.schedule, window, habit.startDate)
                },
                normDays = inWindow.count { it.level == EntryLevel.Norm },
                minimumDays = inWindow.count { it.level == EntryLevel.Minimum },
                weekdayHistogram = done.groupingBy { it.dayOfWeek }.eachCount(),
                plannedPerWeek = habit.schedule.perWeek.toInt(),
            )
        }

    private fun barsFor(
        period: StatPeriod,
        today: LocalDate,
        habitList: List<Habit>,
        marks: Map<String, List<Mark>>,
    ): List<StatBar> {
        val buckets = when (period) {
            StatPeriod.Week -> (13 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                DayWindow(day, day) to (back >= 7)
            }
            StatPeriod.Month -> (7 downTo 0).map { back ->
                val weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1L).minusWeeks(back.toLong())
                DayWindow(weekStart, weekStart.plusDays(6)) to (back >= 4)
            }
            StatPeriod.Year -> (23 downTo 0).map { back ->
                val monthStart = today.withDayOfMonth(1).minusMonths(back.toLong())
                DayWindow(monthStart, monthStart.plusMonths(1).minusDays(1)) to (back >= 12)
            }
        }
        return buckets.map { (window, isPrevious) ->
            StatBar(
                label = when (period) {
                    StatPeriod.Week -> window.from.dayOfMonth.toString()
                    StatPeriod.Month -> window.from.dayOfMonth.toString()
                    StatPeriod.Year -> window.from.monthValue.toString()
                },
                value = averageTempo(habitList, marks, window),
                isPrevious = isPrevious,
            )
        }
    }

    private fun monthlyHistory(
        categoryId: String?,
        transactions: List<Transaction>,
        today: LocalDate,
    ): List<Long> = (1..3).map { back ->
        val monthStart = today.withDayOfMonth(1).minusMonths(back.toLong())
        FinanceAnalytics
            .spendByCategory(transactions, monthStart, monthStart.plusMonths(1).minusDays(1))[categoryId]
            ?: 0L
    }

    private fun boundsFor(period: StatPeriod, today: LocalDate): DayWindow = when (period) {
        StatPeriod.Week -> DayWindow(today.with(WeekFields.ISO.dayOfWeek(), 1L), today)
        StatPeriod.Month -> DayWindow(today.withDayOfMonth(1), today)
        StatPeriod.Year -> DayWindow(today.withDayOfYear(1), today)
    }

    private fun previousBounds(period: StatPeriod, current: DayWindow): DayWindow {
        val length = ChronoUnit.DAYS.between(current.from, current.to)
        val start = when (period) {
            StatPeriod.Week -> current.from.minusWeeks(1)
            StatPeriod.Month -> current.from.minusMonths(1)
            StatPeriod.Year -> current.from.minusYears(1)
        }
        return DayWindow(start, start.plusDays(length))
    }

    private data class Data(
        val habits: List<Habit>,
        val marks: Map<String, List<Mark>>,
        val goals: List<Goal>,
        val stages: Map<String, List<Stage>>,
        val transactions: List<Transaction>,
        val categories: List<Category>,
    )

    companion object {
        private const val IN_TEMPO = 0.5

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                StatsViewModel(
                    habits = app.container.habitRepository,
                    goals = app.container.goalRepository,
                    finance = app.container.financeRepository,
                    settings = app.container.settings,
                )
            }
        }
    }
}
