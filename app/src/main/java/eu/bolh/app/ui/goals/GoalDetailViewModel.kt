package eu.bolh.app.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.analytics.DeadlineAnalytics
import eu.bolh.app.domain.analytics.GoalAnalytics
import eu.bolh.app.domain.analytics.GoalCostAnalytics
import eu.bolh.app.domain.analytics.HabitAnalytics
import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.GoalForecast
import eu.bolh.app.domain.model.GoalSnapshot
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.LinkedHabitCost
import eu.bolh.app.domain.model.Mark
import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.GoalCostItem
import eu.bolh.app.domain.model.SavingsJar
import eu.bolh.app.domain.model.Stage
import eu.bolh.app.domain.model.Task
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StageState(val stage: Stage, val tasks: List<Task>)

data class GoalDetailUiState(
    val goal: Goal? = null,
    val stages: List<StageState> = emptyList(),
    val progress: Double? = null,
    val tempo: Double? = null,
    val forecast: GoalForecast? = null,
    val minutesPerWeek: Int? = null,
    val habitsWithoutEstimate: Int = 0,
    val linkedHabits: List<Habit> = emptyList(),
    /** Привычки, не занятые ни одной целью, — их и предлагаем привязать. */
    val freeHabits: List<Habit> = emptyList(),
    val jar: SavingsJar? = null,
    val jarCurrentMinor: Long = 0L,
    val moneyPerMonthMinor: Long = 0L,
    /** Смета: из чего складывается цена цели и что уже закрыто (§4.5). */
    val costItems: List<CostItemState> = emptyList(),
    val baseCurrency: String = "EUR",
    val today: LocalDate = LocalDate.now(),
) {
    val isOverdue: Boolean
        get() = goal?.deadline?.isBefore(today) == true

    /** Сколько стоит цель по смете. */
    val costPlannedMinor: Long get() = costItems.sumOf { it.item.plannedMinor }

    /** Сколько по смете уже закрыто. */
    val costPaidMinor: Long get() = costItems.sumOf { it.paidMinor }

    /**
     * Взносы, не привязанные ни к одной статье.
     *
     * Их видно отдельно: иначе «в копилке 500, по смете закрыто 0» выглядит
     * как потеря денег, хотя деньги на месте — просто не расписаны.
     */
    val unassignedMinor: Long get() = (jarCurrentMinor - costPaidMinor).coerceAtLeast(0L)
}

/** Статья сметы вместе с тем, сколько по ней уже внесено. */
data class CostItemState(val item: GoalCostItem, val paidMinor: Long) {
    val isClosed: Boolean get() = item.plannedMinor > 0L && paidMinor >= item.plannedMinor

    val share: Float
        get() = if (item.plannedMinor <= 0L) {
            0f
        } else {
            (paidMinor.toFloat() / item.plannedMinor).coerceIn(0f, 1f)
        }
}

/** Всё про деньги цели одним снимком: копилка, её история и смета. */
private data class JarSnapshot(
    val jar: SavingsJar?,
    val contributions: List<Contribution>,
    val costItems: List<GoalCostItem>,
)

class GoalDetailViewModel(
    private val goals: GoalRepository,
    private val habits: HabitRepository,
    private val finance: FinanceRepository,
    private val goalId: String,
) : ViewModel() {

    private val _newStage = MutableStateFlow("")
    val newStage: StateFlow<String> = _newStage.asStateFlow()

    /** Дата, которую приложение предложит вместо просроченной (§4.6). */
    private val _suggestedDeadline = MutableStateFlow<LocalDate?>(null)
    val suggestedDeadline: StateFlow<LocalDate?> = _suggestedDeadline.asStateFlow()

    private val habitData = combine(
        habits.observeAllHabits(),
        habits.observeMarks(from = habits.today().minusDays(30), to = habits.today()),
    ) { habitList, marks -> habitList to marks }

    private val jarData = combine(
        finance.observeJars(),
        finance.observeContributions(),
        goals.observeCostItems(goalId),
    ) { jars, contributions, costItems ->
        val jar = jars.firstOrNull { it.goalId == goalId }
        JarSnapshot(
            jar = jar,
            contributions = jar?.let { contributions[it.id] }.orEmpty(),
            costItems = costItems,
        )
    }

    val state: StateFlow<GoalDetailUiState> = combine(
        goals.observeGoal(goalId),
        goals.observeStagesForGoal(goalId),
        goals.observeTasks(),
        habitData,
        jarData,
    ) { goal, stages, tasksByStage, habitPair, jarSnapshot ->
        val (habitList, marks) = habitPair
        val jar = jarSnapshot.jar
        val contributions = jarSnapshot.contributions
        val today = goals.today()
        val window = DayWindow.lastDays(today, 30)
        val ordered = stages.sortedBy { it.sortOrder }
        val closed = ordered.count { it.isDone }
        val linked = habitList.filter { it.goalId == goalId }
        val jarCurrent = contributions.sumOf { it.amountMinor }

        GoalDetailUiState(
            goal = goal,
            stages = ordered.map { stage ->
                StageState(stage, tasksByStage[stage.id].orEmpty().sortedBy { it.sortOrder })
            },
            progress = GoalAnalytics.progress(ordered.size, closed),
            tempo = GoalAnalytics.tempo(temposOf(linked, marks, window)),
            forecast = goal?.let {
                GoalAnalytics.forecast(
                    GoalSnapshot(
                        startDate = it.startDate,
                        today = today,
                        deadline = it.deadline,
                        stagesTotal = ordered.size,
                        stagesClosed = closed,
                        jarTargetMinor = jar?.targetMinor,
                        jarCurrentMinor = jar?.let { jarCurrent },
                        contributions = contributions,
                    ),
                )
            },
            minutesPerWeek = GoalCostAnalytics.minutesPerWeek(costsOf(linked)),
            habitsWithoutEstimate = GoalCostAnalytics.habitsWithoutEstimate(costsOf(linked)),
            linkedHabits = linked,
            freeHabits = habitList.filter { it.goalId == null },
            jar = jar,
            jarCurrentMinor = jarCurrent,
            moneyPerMonthMinor = jar?.plannedMonthlyMinor ?: 0L,
            costItems = jarSnapshot.costItems.map { item ->
                CostItemState(
                    item = item,
                    // Закрытое по статье — это сумма привязанных к ней взносов.
                    paidMinor = contributions
                        .filter { it.costItemId == item.id }
                        .sumOf { it.amountMinor },
                )
            },
            // Смета живёт в валюте копилки: цель стоит столько, сколько на неё
            // собирают, а не столько же в валюте приложения.
            baseCurrency = jar?.currencyCode ?: finance.baseCurrency,
            today = today,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalDetailUiState(),
    )

    init {
        observeSuggestion()
    }

    private fun temposOf(
        linked: List<Habit>,
        marks: Map<String, List<Mark>>,
        window: DayWindow,
    ): List<Double> = linked.mapNotNull { habit ->
        HabitAnalytics.tempo(
            marks = HabitAnalytics.completeDays(marks[habit.id].orEmpty(), habit.timesPerDay),
            schedule = habit.schedule,
            window = window,
            startDate = habit.startDate,
        )
    }

    private fun costsOf(linked: List<Habit>): List<LinkedHabitCost> =
        linked.map { LinkedHabitCost(it.schedule, it.estimatedMinutes) }

    /**
     * Просроченной цели предлагается новый срок, уже поправленный на коэффициент (§4.6).
     * Пока коэффициента нет — берётся полтора раза: это честнее, чем предлагать
     * ровно столько же, сколько человек уже не успел.
     */
    private fun observeSuggestion() {
        viewModelScope.launch {
            val factor = DeadlineAnalytics.factor(goals.completedDeadlines()) ?: DEFAULT_FACTOR
            goals.observeGoal(goalId).collect { goal ->
                val deadline = goal?.deadline
                val today = goals.today()
                _suggestedDeadline.value = if (deadline != null && deadline.isBefore(today)) {
                    DeadlineAnalytics.rescheduleFor(
                        start = goal.startDate,
                        originalDeadline = deadline,
                        today = today,
                        factor = factor,
                    )
                } else {
                    null
                }
            }
        }
    }

    fun setNewStage(value: String) {
        _newStage.value = value
    }

    fun addStage() {
        val name = _newStage.value.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            goals.addStage(goalId, name)
            _newStage.value = ""
        }
    }

    fun toggleStage(stage: Stage) {
        viewModelScope.launch { goals.setStageDone(stage.id, !stage.isDone) }
    }

    /**
     * Удаление цели: этапы, задачи и копилка уходят вместе с ней.
     *
     * Оставленная копилка превращается в строку без имени, которую нечем
     * убрать: цели, к которой она привязана, уже нет.
     */
    fun delete() {
        viewModelScope.launch {
            state.value.jar?.let { finance.deleteJar(it.id) }
            goals.delete(goalId)
        }
    }

    /**
     * Привязка привычки к цели. Привычка принадлежит одной цели: иначе темп
     * цели считался бы по чужой работе, и обе цифры перестали бы что-то значить.
     */
    fun linkHabit(habitId: String) {
        viewModelScope.launch {
            habits.findHabit(habitId)?.let { habits.update(it.copy(goalId = goalId)) }
        }
    }

    fun unlinkHabit(habitId: String) {
        viewModelScope.launch {
            habits.findHabit(habitId)?.let { habits.update(it.copy(goalId = null)) }
        }
    }

    fun renameStage(stageId: String, name: String) {
        viewModelScope.launch { goals.renameStage(stageId, name) }
    }

    fun renameTask(taskId: String, name: String) {
        viewModelScope.launch { goals.renameTask(taskId, name) }
    }

    fun deleteStage(stageId: String) {
        viewModelScope.launch { goals.deleteStage(stageId) }
    }

    fun addTask(stageId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { goals.addTask(stageId, name) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { goals.deleteTask(taskId) }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { goals.setTaskDone(task.id, !task.isDone) }
    }

    /** Завести копилку под цель (§6.6). По умолчанию — виртуальный конверт. */
    fun createJar(targetMinor: Long) {
        if (targetMinor <= 0L) return
        viewModelScope.launch { finance.createJar(goalId = goalId, targetMinor = targetMinor) }
    }

    fun setJarTarget(targetMinor: Long) {
        val jarId = state.value.jar?.id ?: return
        viewModelScope.launch { finance.setJarTarget(jarId, targetMinor) }
    }

    fun deleteJar() {
        val jarId = state.value.jar?.id ?: return
        viewModelScope.launch { finance.deleteJar(jarId) }
    }

    fun undoLastContribution() {
        val jarId = state.value.jar?.id ?: return
        viewModelScope.launch { finance.undoLastContribution(jarId) }
    }

    // --- Смета ---------------------------------------------------------

    fun addCostItem(name: String, plannedMinor: Long) {
        if (name.isBlank()) return
        viewModelScope.launch { goals.addCostItem(goalId, name, plannedMinor) }
    }

    fun renameCostItem(itemId: String, name: String) {
        viewModelScope.launch { goals.renameCostItem(itemId, name) }
    }

    fun setCostItemPlanned(itemId: String, plannedMinor: Long) {
        viewModelScope.launch { goals.setCostItemPlanned(itemId, plannedMinor) }
    }

    fun deleteCostItem(itemId: String) {
        viewModelScope.launch { goals.deleteCostItem(itemId) }
    }

    /**
     * Закрыть статью: взнос ровно на недостающую сумму.
     *
     * Без копилки закрывать нечем — смета считает внесённое, а внесённое живёт
     * в копилке. Поэтому копилка заводится сама, с целью на сумму сметы.
     */
    fun payCostItem(itemId: String) {
        val row = state.value.costItems.firstOrNull { it.item.id == itemId } ?: return
        val rest = row.item.plannedMinor - row.paidMinor
        if (rest <= 0L) return
        viewModelScope.launch {
            val jarId = state.value.jar?.id
                ?: finance.createJar(goalId = goalId, targetMinor = state.value.costPlannedMinor)
            finance.addContribution(jarId, rest, costItemId = itemId)
        }
    }

    fun addContribution(amountMinor: Long) {
        val jarId = state.value.jar?.id ?: return
        if (amountMinor <= 0L) return
        viewModelScope.launch { finance.addContribution(jarId, amountMinor) }
    }

    fun reopen() {
        viewModelScope.launch { goals.reopen(goalId) }
    }

    fun complete() {
        viewModelScope.launch { goals.complete(goalId) }
    }

    fun reschedule(date: LocalDate) {
        viewModelScope.launch {
            goals.reschedule(goalId, date)
            _suggestedDeadline.value = null
        }
    }

    companion object {
        const val ARG_GOAL_ID = "goalId"

        /** Пока личного коэффициента нет, перенос берётся с запасом в полтора раза. */
        private const val DEFAULT_FACTOR = 1.5

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                GoalDetailViewModel(
                    goals = app.container.goalRepository,
                    habits = app.container.habitRepository,
                    finance = app.container.financeRepository,
                    goalId = handle.get<String>(ARG_GOAL_ID).orEmpty(),
                )
            }
        }
    }
}

