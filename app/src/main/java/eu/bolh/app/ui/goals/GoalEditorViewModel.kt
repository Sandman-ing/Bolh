package eu.bolh.app.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.domain.analytics.DeadlineAnalytics
import eu.bolh.app.domain.model.DeadlineRecord
import eu.bolh.app.domain.model.Goal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Проверка срока (§4.4). Появляется только когда коэффициент есть — после пяти
 * завершённых целей. До этого механика молчит, а не показывает пустое место.
 */
data class DeadlineCheck(
    val factor: Double,
    val deadlinesSet: Int,
    val deadlinesKept: Int,
    val realistic: LocalDate,
)

/** Этап в черновике цели: имя и задачи, которые ещё нигде не сохранены. */
data class StageDraft(val name: String, val tasks: List<String> = emptyList())

data class GoalDraft(
    val id: String? = null,
    val name: String = "",
    val icon: String = "flag",
    val deadline: LocalDate? = null,
    val startDate: LocalDate? = null,
    val stages: List<StageDraft> = emptyList(),
    val newStage: String = "",
    val stagesExpanded: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank()
}

class GoalEditorViewModel(
    private val repository: GoalRepository,
    private val goalId: String?,
) : ViewModel() {

    private val _draft = MutableStateFlow(GoalDraft(id = goalId))
    val draft: StateFlow<GoalDraft> = _draft.asStateFlow()

    private val _check = MutableStateFlow<DeadlineCheck?>(null)
    val check: StateFlow<DeadlineCheck?> = _check.asStateFlow()

    private var records: List<DeadlineRecord> = emptyList()

    init {
        viewModelScope.launch {
            records = repository.completedDeadlines()
            recomputeCheck()
        }
        if (goalId != null) {
            viewModelScope.launch {
                repository.observeGoal(goalId).collect { goal ->
                    if (goal != null) {
                        _draft.update {
                            it.copy(
                                name = goal.name,
                                icon = goal.icon,
                                deadline = goal.deadline,
                                startDate = goal.startDate,
                            )
                        }
                        recomputeCheck()
                    }
                }
            }
        }
    }

    fun setName(value: String) = _draft.update { it.copy(name = value) }

    fun setIcon(value: String) = _draft.update { it.copy(icon = value) }

    fun setDeadline(value: LocalDate?) {
        _draft.update { it.copy(deadline = value) }
        recomputeCheck()
    }

    fun toggleStages() = _draft.update { it.copy(stagesExpanded = !it.stagesExpanded) }

    fun setNewStage(value: String) = _draft.update { it.copy(newStage = value) }

    fun addStage(fallback: String) = _draft.update { current ->
        // Пустой этап заводится с именем по умолчанию: человек набрасывает
        // скелет цели, а называет потом.
        val name = current.newStage.trim().ifEmpty { fallback }
        current.copy(stages = current.stages + StageDraft(name), newStage = "")
    }

    fun removeStage(index: Int) = _draft.update { current ->
        current.copy(stages = current.stages.filterIndexed { i, _ -> i != index })
    }

    fun renameStage(index: Int, name: String) = _draft.update { current ->
        current.copy(
            stages = current.stages.mapIndexed { i, stage ->
                if (i == index) stage.copy(name = name) else stage
            },
        )
    }

    /** Перестановка этапа: порядок — часть смысла цели, и его правят на ходу. */
    fun moveStage(index: Int, delta: Int) = _draft.update { current ->
        val target = index + delta
        if (target !in current.stages.indices) {
            current
        } else {
            val list = current.stages.toMutableList()
            list.add(target, list.removeAt(index))
            current.copy(stages = list)
        }
    }

    fun addTask(stageIndex: Int, name: String) = _draft.update { current ->
        current.copy(
            stages = current.stages.mapIndexed { i, stage ->
                if (i == stageIndex) stage.copy(tasks = stage.tasks + name) else stage
            },
        )
    }

    fun removeTask(stageIndex: Int, taskIndex: Int) = _draft.update { current ->
        current.copy(
            stages = current.stages.mapIndexed { i, stage ->
                if (i != stageIndex) {
                    stage
                } else {
                    stage.copy(tasks = stage.tasks.filterIndexed { j, _ -> j != taskIndex })
                }
            },
        )
    }

    /**
     * Кнопка отказа не серая и не спрятана: человек имеет право быть оптимистом,
     * и его выбор тоже станет данными (§4.4).
     */
    fun acceptRealisticDate() {
        _check.value?.let { setDeadline(it.realistic) }
    }

    fun save() {
        val draft = _draft.value
        if (!draft.canSave) return
        viewModelScope.launch {
            val today = repository.today()
            val goal = Goal(
                id = draft.id ?: UUID.randomUUID().toString(),
                name = draft.name.trim(),
                icon = draft.icon,
                startDate = draft.startDate ?: today,
                deadline = draft.deadline,
            )
            if (draft.isEditing) {
                repository.update(goal)
            } else {
                repository.create(goal)
                // Этапы и их задачи сохраняются в том порядке, в котором
                // человек их выстроил: порядок и есть план.
                draft.stages.forEach { stage ->
                    val stageId = repository.addStage(goal.id, stage.name)
                    stage.tasks.forEach { repository.addTask(stageId, it) }
                }
            }
            _draft.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = goalId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _draft.update { it.copy(saved = true) }
        }
    }

    private fun recomputeCheck() {
        val draft = _draft.value
        val deadline = draft.deadline
        val factor = DeadlineAnalytics.factor(records)
        if (deadline == null || factor == null) {
            _check.value = null
            return
        }
        val start = draft.startDate ?: repository.today()
        val realistic = DeadlineAnalytics.realisticDeadline(start, deadline, factor)
        // Если поправка ничего не меняет, показывать нечего.
        _check.value = if (realistic > deadline) {
            DeadlineCheck(
                factor = factor,
                deadlinesSet = records.size,
                deadlinesKept = DeadlineAnalytics.keptCount(records),
                realistic = realistic,
            )
        } else {
            null
        }
    }

    companion object {
        const val ARG_GOAL_ID = "goalId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                GoalEditorViewModel(
                    repository = app.container.goalRepository,
                    goalId = handle.get<String>(ARG_GOAL_ID)?.takeIf { it.isNotBlank() && it != "new" },
                )
            }
        }
    }
}
