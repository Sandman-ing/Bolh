package eu.bolh.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.analytics.HabitAnalytics
import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.Mark
import eu.bolh.app.domain.model.Pause
import eu.bolh.app.domain.model.ScheduleType
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Одна строка списка привычек со всем, что нужно нарисовать. */
data class HabitRowState(
    val habit: Habit,
    val weekMarks: Map<LocalDate, EntryLevel>,
    val selectedLevel: EntryLevel?,
    val streak: Int?,
    val tempo30: Double?,
    val depth30: Double?,
    val done30: Int,
    val planned30: Int,
    val needsRecovery: Boolean,
    val isPaused: Boolean,
    val slipsThisWeek: Int,
    val daysWithoutSlip: Int,
    val savedMinor: Long?,
    /** Запланирована ли привычка на выбранный день. */
    val plannedOnDay: Boolean,
)

/** Сколько привычек на день запланировано и сколько уже закрыто. */
data class DayProgress(val planned: Int, val done: Int) {
    val isComplete: Boolean get() = planned > 0 && done >= planned
    val isPartial: Boolean get() = done in 1 until planned
}

data class HabitsUiState(
    val today: LocalDate,
    val selectedDate: LocalDate,
    val weekDays: List<LocalDate> = emptyList(),
    val good: List<HabitRowState> = emptyList(),
    val bad: List<HabitRowState> = emptyList(),
    val weekProgress: Map<LocalDate, DayProgress> = emptyMap(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && good.isEmpty() && bad.isEmpty()

    /** Полезные привычки, которых сегодня ждут. */
    val goodToday: List<HabitRowState> get() = good.filter { it.plannedOnDay }

    /** Остальные — они не пропали, просто сегодня их никто не спрашивает. */
    val goodOtherDays: List<HabitRowState> get() = good.filterNot { it.plannedOnDay }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(private val repository: HabitRepository) : ViewModel() {

    /** Окно истории, которого хватает и месячному темпу, и трёхмесячной частоте срывов. */
    private val historyDays = 120L

    private val selectedDate = MutableStateFlow(repository.today())

    val state: StateFlow<HabitsUiState> = selectedDate
        .flatMapLatest { selected -> stateFor(selected) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HabitsUiState(
                today = repository.today(),
                selectedDate = repository.today(),
            ),
        )

    private fun stateFor(selected: LocalDate) = run {
        val today = repository.today()
        val weekStart = selected.with(WeekFields.ISO.dayOfWeek(), 1L)
        val weekDays = (0L..6L).map { weekStart.plusDays(it) }
        val from = minOf(weekStart, today.minusDays(historyDays))
        val to = maxOf(weekDays.last(), today)

        combine(
            repository.observeAllHabits(),
            repository.observeMarks(from, to),
            repository.observePauses(),
        ) { habits, marks, pauses ->
            val rows = habits.map { habit ->
                rowState(
                    habit = habit,
                    marks = marks[habit.id].orEmpty(),
                    pauses = pauses[habit.id].orEmpty(),
                    weekDays = weekDays,
                    selected = selected,
                    today = today,
                )
            }
            HabitsUiState(
                today = today,
                selectedDate = selected,
                weekDays = weekDays,
                good = rows.filter { it.habit.kind == HabitKind.Good },
                bad = rows.filter { it.habit.kind == HabitKind.Bad },
                weekProgress = weekDays.associateWith { day ->
                    progressOn(day, habits, marks, pauses)
                },
                loaded = true,
            )
        }
    }

    private fun rowState(
        habit: Habit,
        marks: List<Mark>,
        pauses: List<Pause>,
        weekDays: List<LocalDate>,
        selected: LocalDate,
        today: LocalDate,
    ): HabitRowState {
        val window = DayWindow.lastDays(today, 30)
        val slips = marks.filter { it.level == EntryLevel.Slip }.map { it.date }
        val weekStart = weekDays.first()

        val weekMarks = weekDays.mapNotNull { day ->
            marks.filter { it.date == day }
                .maxByOrNull { levelWeight(it.level) }
                ?.let { day to it.level }
        }.toMap()

        return HabitRowState(
            habit = habit,
            weekMarks = weekMarks,
            selectedLevel = weekMarks[selected] ?: marks.firstOrNull { it.date == selected }?.level,
            streak = HabitAnalytics.streak(
                mode = habit.mode,
                marks = marks,
                schedule = habit.schedule,
                today = today,
                startDate = habit.startDate,
                pauses = pauses,
                skipAllowancePerMonth = habit.skipAllowancePerMonth,
            ),
            tempo30 = HabitAnalytics.tempo(marks, habit.schedule, window, habit.startDate, pauses),
            depth30 = HabitAnalytics.depth(marks, window, habit.startDate, pauses),
            done30 = HabitAnalytics.doneDays(marks, window, habit.startDate, pauses),
            planned30 = HabitAnalytics.plannedDaysRounded(habit.schedule, window, habit.startDate, pauses),
            isPaused = pauses.any { it.covers(selected) },
            needsRecovery = habit.mode == HabitMode.Minimum &&
                HabitAnalytics.needsRecovery(marks, habit.schedule, today, habit.startDate, pauses),
            slipsThisWeek = HabitAnalytics.slipsInWeek(slips, weekStart),
            daysWithoutSlip = HabitAnalytics.daysWithoutSlip(slips, today, habit.startDate),
            savedMinor = habit.costPerOccurrenceMinor?.let { cost ->
                HabitAnalytics.avoidedCostMinor(slips, today, habit.startDate, cost)
            },
            plannedOnDay = plannedOn(habit, selected, pauses),
        )
    }

    /**
     * Ждут ли привычку в этот день (§5.5).
     *
     * «Выбранные дни» — только в свои дни: зал в среду не должен требовать
     * отметки в четверг. «Каждый день» и «N раз в неделю» спрашиваются всегда:
     * у второго нет закреплённых дней, человек сам решает, когда.
     */
    private fun plannedOn(habit: Habit, date: LocalDate, pauses: List<Pause>): Boolean {
        if (pauses.any { it.covers(date) }) return false
        if (date.isBefore(habit.startDate)) return false
        return when (habit.schedule.type) {
            ScheduleType.Weekdays -> habit.schedule.plannedOn(date)
            else -> true
        }
    }

    private fun progressOn(
        day: LocalDate,
        habits: List<Habit>,
        marks: Map<String, List<Mark>>,
        pauses: Map<String, List<Pause>>,
    ): DayProgress {
        val expected = habits.filter { habit ->
            habit.kind == HabitKind.Good && plannedOn(habit, day, pauses[habit.id].orEmpty())
        }
        val done = expected.count { habit ->
            marks[habit.id].orEmpty().any { it.date == day && it.level != EntryLevel.Slip }
        }
        return DayProgress(planned = expected.size, done = done)
    }

    private fun levelWeight(level: EntryLevel): Int = when (level) {
        EntryLevel.Norm -> 2
        EntryLevel.Minimum -> 1
        EntryLevel.Slip -> 0
    }

    fun selectDate(date: LocalDate) {
        // Будущее не отмечают: день ещё не наступил.
        if (date.isAfter(repository.today())) return
        selectedDate.value = date
    }

    fun showPreviousWeek() {
        selectedDate.value = selectedDate.value.minusWeeks(1)
    }

    fun showNextWeek() {
        val next = selectedDate.value.plusWeeks(1)
        selectedDate.value = if (next.isAfter(repository.today())) repository.today() else next
    }

    fun mark(habit: Habit, level: EntryLevel) {
        viewModelScope.launch {
            repository.markDay(habit.id, selectedDate.value, level)
        }
    }

    fun addSlip(habit: Habit) {
        viewModelScope.launch {
            repository.addSlip(habit.id, selectedDate.value)
        }
    }

    fun undoSlip(habit: Habit) {
        viewModelScope.launch {
            repository.undoLastSlip(habit.id, selectedDate.value)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                HabitsViewModel(app.container.habitRepository)
            }
        }
    }
}

