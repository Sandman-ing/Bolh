package eu.bolh.app.ui.goals

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
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.analytics.GoalAnalytics
import eu.bolh.app.domain.analytics.HabitAnalytics
import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.GoalForecast
import eu.bolh.app.domain.model.GoalSnapshot
import eu.bolh.app.domain.model.GoalSort
import eu.bolh.app.domain.model.GoalStatus
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.Stage
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Карточка цели (§4.7). Два индикатора вместо одного: прогресс отвечает
 * «сколько сделано», темп — «двигаюсь ли я вообще». Одно число из них
 * не собирается принципиально.
 */
data class GoalCardState(
    val goal: Goal,
    val progress: Double?,
    val tempo: Double?,
    val stagesTotal: Int,
    val stagesClosed: Int,
    val nextStage: Stage?,
    val forecast: GoalForecast?,
    val linkedHabits: List<Habit>,
    val isOverdue: Boolean,
    /** Привычки цели, закрытые сегодня. */
    val habitsDoneToday: Set<String> = emptySet(),
    /** Дней до срока; меньше нуля — срок прошёл. Без срока — null. */
    val daysLeft: Long? = null,
    /** Номер следующего этапа по порядку, с единицы. */
    val nextStageNumber: Int = 0,
    /** Бюджет цели, если он заведён. */
    val budget: GoalBudget? = null,
)

/**
 * Бюджет на карточке: сколько собрано из нужного (§6.6). Считается так же,
 * как на экране цели, — по истории пополнений копилки.
 */
data class GoalBudget(
    val collectedMinor: Long,
    val targetMinor: Long,
    val currencyCode: String,
) {
    val isCollected: Boolean get() = targetMinor > 0L && collectedMinor >= targetMinor
}

/** Достигнутая цель не исчезает: она переезжает вниз и остаётся историей (§4.6). */
data class CompletedGoalState(
    val goal: Goal,
    val stagesTotal: Int,
    val stagesClosed: Int,
)

data class GoalsUiState(
    val goals: List<GoalCardState> = emptyList(),
    val completed: List<CompletedGoalState> = emptyList(),
    val sort: GoalSort = GoalSort.Manual,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && goals.isEmpty() && completed.isEmpty()
}

class GoalsViewModel(
    private val goals: GoalRepository,
    private val habits: HabitRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    /** Бюджеты по целям. У цели одна копилка — берётся первая, как на экране цели. */
    private val budgets = combine(
        finance.observeJars(),
        finance.observeContributions(),
    ) { jars, contributions ->
        jars.distinctBy { it.goalId }.associate { jar ->
            jar.goalId to GoalBudget(
                collectedMinor = FinanceAnalytics.jarBalance(jar.id, contributions),
                targetMinor = jar.targetMinor,
                currencyCode = jar.currencyCode,
            )
        }
    }

    private val sortAndBudgets = combine(settings.goalSort, budgets) { sort, byGoal -> sort to byGoal }

    val state: StateFlow<GoalsUiState> = combine(
        goals.observeAllGoals(),
        goals.observeStages(),
        habits.observeAllHabits(),
        habits.observeMarks(
            from = habits.today().minusDays(HABIT_WINDOW_DAYS),
            to = habits.today(),
        ),
        sortAndBudgets,
    ) { allGoals, stagesByGoal, habitList, marksByHabit, (sort, budgetsByGoal) ->
        val today = goals.today()
        val window = DayWindow.lastDays(today, 30)
        val goalList = allGoals.filter { it.status == GoalStatus.Active }

        val cards = goalList.map { goal ->
            val stages = stagesByGoal[goal.id].orEmpty().sortedBy { it.sortOrder }
            val closed = stages.count { it.isDone }
            val linked = habitList.filter { it.goalId == goal.id }

            val tempos = linked.mapNotNull { habit ->
                HabitAnalytics.tempo(
                    marks = HabitAnalytics.completeDays(marksByHabit[habit.id].orEmpty(), habit.timesPerDay),
                    schedule = habit.schedule,
                    window = window,
                    startDate = habit.startDate,
                )
            }

            val next = stages.firstOrNull { !it.isDone }

            GoalCardState(
                goal = goal,
                progress = GoalAnalytics.progress(stages.size, closed),
                tempo = GoalAnalytics.tempo(tempos),
                stagesTotal = stages.size,
                stagesClosed = closed,
                nextStage = next,
                nextStageNumber = next?.let { stages.indexOf(it) + 1 } ?: 0,
                forecast = GoalAnalytics.forecast(
                    GoalSnapshot(
                        startDate = goal.startDate,
                        today = today,
                        deadline = goal.deadline,
                        stagesTotal = stages.size,
                        stagesClosed = closed,
                    ),
                ),
                linkedHabits = linked,
                isOverdue = goal.deadline?.isBefore(today) == true,
                habitsDoneToday = linked
                    .filter { habit ->
                        marksByHabit[habit.id].orEmpty().count {
                            it.date == today && it.level != EntryLevel.Slip
                        } >= habit.timesPerDay
                    }
                    .mapTo(HashSet()) { it.id },
                daysLeft = goal.deadline?.let { ChronoUnit.DAYS.between(today, it) },
                budget = budgetsByGoal[goal.id],
            )
        }

        GoalsUiState(
            goals = cards.sortedWith(comparator(sort)),
            completed = allGoals
                .filter { it.status == GoalStatus.Done }
                .sortedByDescending { it.doneAt }
                .map { goal ->
                    val stages = stagesByGoal[goal.id].orEmpty()
                    CompletedGoalState(
                        goal = goal,
                        stagesTotal = stages.size,
                        stagesClosed = stages.count { it.isDone },
                    )
                },
            sort = sort,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalsUiState(),
    )

    fun setSort(sort: GoalSort) {
        viewModelScope.launch { settings.setGoalSort(sort) }
    }

    /** Этап закрывается прямо с карточки, не заходя в цель. */
    fun setStageDone(stage: Stage, done: Boolean) {
        viewModelScope.launch { goals.setStageDone(stage.id, done) }
    }

    fun completeGoal(goalId: String) {
        viewModelScope.launch { goals.complete(goalId) }
    }

    private fun comparator(sort: GoalSort): Comparator<GoalCardState> = when (sort) {
        GoalSort.Manual -> compareBy { it.goal.sortOrder }
        // Цели без срока уходят вниз: сортировка по сроку не должна их выдумывать.
        GoalSort.Deadline -> compareBy(nullsLast()) { it.goal.deadline }
        // Сначала те, что встали: экран нужен для решений, а не для любования.
        GoalSort.Tempo -> compareBy(nullsLast()) { it.tempo }
    }

    companion object {
        private const val HABIT_WINDOW_DAYS = 30L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                GoalsViewModel(
                    goals = app.container.goalRepository,
                    habits = app.container.habitRepository,
                    finance = app.container.financeRepository,
                    settings = app.container.settings,
                )
            }
        }
    }
}

