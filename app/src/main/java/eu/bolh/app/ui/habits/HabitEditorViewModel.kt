package eu.bolh.app.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.notifications.HabitReminderScheduler
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.Pause
import eu.bolh.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Черновик привычки. Быстрый путь трогает три верхних поля, остальное живёт под кнопкой. */
data class HabitDraft(
    val id: String? = null,
    val name: String = "",
    val icon: String = "wb_sunny",
    val kind: HabitKind = HabitKind.Good,
    val mode: HabitMode = HabitMode.Game,
    val scheduleType: ScheduleType = ScheduleType.Daily,
    val days: Set<DayOfWeek> = emptySet(),
    val timesPerWeek: Int = 3,
    val minimumLabel: String = "",
    val normLabel: String = "",
    val limitPerWeek: Int = 3,
    val costMinor: Long? = null,
    val estimatedMinutes: Int? = null,
    val skipAllowancePerMonth: Int = 2,
    val reminderMinuteOfDay: Int? = null,
    val reminderRepeatEvening: Boolean = false,
    val goalId: String? = null,
    val advancedExpanded: Boolean = false,
    val saved: Boolean = false,
    /** У существующей привычки дата старта не трогается: на ней стоит вся статистика. */
    val startDate: LocalDate? = null,
) {
    val isEditing: Boolean get() = id != null

    val canSave: Boolean
        get() = name.isNotBlank() &&
            (scheduleType != ScheduleType.Weekdays || days.isNotEmpty())

    /** Режимы, доступные этому виду привычки: у вредных своя логика счёта (§5.4). */
    val availableModes: List<HabitMode>
        get() = if (kind == HabitKind.Good) {
            listOf(HabitMode.Game, HabitMode.Skips, HabitMode.Minimum)
        } else {
            listOf(HabitMode.Counter, HabitMode.Limit)
        }
}

class HabitEditorViewModel(
    private val repository: HabitRepository,
    private val goals: GoalRepository,
    private val reminders: HabitReminderScheduler,
    private val habitId: String?,
) : ViewModel() {

    private val _draft = MutableStateFlow(HabitDraft(id = habitId))
    val draft: StateFlow<HabitDraft> = _draft.asStateFlow()

    /** Привязка к цели — дело добровольное: по умолчанию блоки живут отдельно (§2). */
    val availableGoals: StateFlow<List<Goal>> = goals.observeActiveGoals().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _pause = MutableStateFlow<Pause?>(null)
    val pause: StateFlow<Pause?> = _pause.asStateFlow()

    init {
        if (habitId != null) {
            viewModelScope.launch {
                _pause.value = repository.pausesFor(habitId).maxByOrNull { it.to }
            }
            viewModelScope.launch {
                repository.observeHabit(habitId).collect { habit ->
                    if (habit != null) _draft.value = habit.toDraft()
                }
            }
        }
    }

    fun setName(value: String) = _draft.update { it.copy(name = value) }

    fun setIcon(value: String) = _draft.update { it.copy(icon = value) }

    fun setKind(value: HabitKind) = _draft.update { current ->
        val mode = if (value == HabitKind.Good) HabitMode.Game else HabitMode.Counter
        current.copy(kind = value, mode = mode)
    }

    fun setMode(value: HabitMode) = _draft.update { it.copy(mode = value) }

    fun setScheduleType(value: ScheduleType) = _draft.update { it.copy(scheduleType = value) }

    fun toggleDay(day: DayOfWeek) = _draft.update { current ->
        val days = if (day in current.days) current.days - day else current.days + day
        current.copy(days = days)
    }

    fun setTimesPerWeek(value: Int) = _draft.update { it.copy(timesPerWeek = value.coerceIn(1, 7)) }

    fun setMinimumLabel(value: String) = _draft.update { it.copy(minimumLabel = value) }

    fun setNormLabel(value: String) = _draft.update { it.copy(normLabel = value) }

    fun setLimitPerWeek(value: Int) = _draft.update { it.copy(limitPerWeek = value.coerceIn(1, 20)) }

    fun setCostMinor(value: Long?) = _draft.update { it.copy(costMinor = value) }

    fun setEstimatedMinutes(value: Int?) = _draft.update { it.copy(estimatedMinutes = value) }

    fun setReminder(minuteOfDay: Int?) = _draft.update { it.copy(reminderMinuteOfDay = minuteOfDay) }

    fun setEveningRepeat(value: Boolean) = _draft.update { it.copy(reminderRepeatEvening = value) }

    /**
     * Дата начала привычки.
     *
     * Нужна, чтобы отметить дни до установки приложения: человек читает книгу
     * третий день, а приложение поставил вчера. Дальше сегодняшнего дня
     * начало не уезжает — будущее отмечать нечем.
     */
    fun setStartDate(value: LocalDate) = _draft.update { it.copy(startDate = value) }

    fun toggleAdvanced() = _draft.update { it.copy(advancedExpanded = !it.advancedExpanded) }

    fun setGoal(id: String?) = _draft.update { it.copy(goalId = if (it.goalId == id) null else id) }

    /** Пауза считается от сегодняшнего дня: задним числом её ставить незачем. */
    fun pauseUntil(date: java.time.LocalDate) {
        val id = habitId ?: return
        viewModelScope.launch {
            val from = repository.today()
            repository.addPause(id, from, date)
            _pause.value = Pause(from, date)
        }
    }

    fun clearPause() {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.clearPauses(id)
            _pause.value = null
        }
    }

    /** Шаблон заполняет всё сразу — тексты приходят уже разрешёнными из ресурсов (§9.6). */
    fun applyTemplate(
        name: String,
        icon: String,
        kind: HabitKind,
        mode: HabitMode,
        schedule: HabitSchedule,
        minimumLabel: String?,
        normLabel: String?,
        estimatedMinutes: Int?,
        limitPerWeek: Int?,
    ) = _draft.update { current ->
        current.copy(
            name = name,
            icon = icon,
            kind = kind,
            mode = mode,
            scheduleType = schedule.type,
            days = schedule.days,
            timesPerWeek = schedule.timesPerWeek ?: current.timesPerWeek,
            minimumLabel = minimumLabel.orEmpty(),
            normLabel = normLabel.orEmpty(),
            estimatedMinutes = estimatedMinutes,
            limitPerWeek = limitPerWeek ?: current.limitPerWeek,
        )
    }

    fun save() {
        val draft = _draft.value
        if (!draft.canSave) return
        viewModelScope.launch {
            val habit = draft.toHabit(today = repository.today())
            if (draft.isEditing) repository.update(habit) else repository.create(habit)
            reminders.schedule(habit)
            _draft.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.delete(id)
            reminders.cancel(id)
            _draft.update { it.copy(saved = true) }
        }
    }

    private fun Habit.toDraft() = HabitDraft(
        id = id,
        name = name,
        icon = icon,
        kind = kind,
        mode = mode,
        scheduleType = schedule.type,
        days = schedule.days,
        timesPerWeek = schedule.timesPerWeek ?: 3,
        minimumLabel = minimumLabel.orEmpty(),
        normLabel = normLabel.orEmpty(),
        limitPerWeek = limitPerWeek ?: 3,
        costMinor = costPerOccurrenceMinor,
        estimatedMinutes = estimatedMinutes,
        skipAllowancePerMonth = skipAllowancePerMonth,
        reminderMinuteOfDay = reminderMinuteOfDay,
        reminderRepeatEvening = reminderRepeatEvening,
        goalId = goalId,
        startDate = startDate,
    )

    private fun HabitDraft.toHabit(today: LocalDate) = Habit(
        id = id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        icon = icon,
        kind = kind,
        mode = mode,
        schedule = HabitSchedule(
            type = scheduleType,
            days = if (scheduleType == ScheduleType.Weekdays) days else emptySet(),
            timesPerWeek = if (scheduleType == ScheduleType.TimesPerWeek) timesPerWeek else null,
        ),
        minimumLabel = minimumLabel.takeIf { it.isNotBlank() },
        normLabel = normLabel.takeIf { it.isNotBlank() },
        estimatedMinutes = estimatedMinutes,
        costPerOccurrenceMinor = costMinor,
        costCurrencyCode = costMinor?.let { "EUR" },
        limitPerWeek = limitPerWeek.takeIf { mode == HabitMode.Limit },
        skipAllowancePerMonth = if (mode == HabitMode.Skips) skipAllowancePerMonth else 0,
        reminderMinuteOfDay = reminderMinuteOfDay,
        reminderRepeatEvening = reminderRepeatEvening,
        goalId = goalId,
        startDate = startDate ?: today,
    )

    companion object {
        const val ARG_HABIT_ID = "habitId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                HabitEditorViewModel(
                    repository = app.container.habitRepository,
                    goals = app.container.goalRepository,
                    reminders = app.container.reminderScheduler,
                    habitId = handle.get<String>(ARG_HABIT_ID)?.takeIf { it.isNotBlank() && it != "new" },
                )
            }
        }
    }
}
