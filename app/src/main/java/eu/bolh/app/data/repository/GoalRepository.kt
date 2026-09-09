package eu.bolh.app.data.repository

import eu.bolh.app.data.db.dao.GoalDao
import eu.bolh.app.data.db.entity.DeadlineRecordEntity
import eu.bolh.app.data.db.entity.GoalEntity
import eu.bolh.app.data.db.entity.StageEntity
import eu.bolh.app.data.db.entity.TaskEntity
import eu.bolh.app.domain.model.DeadlineRecord
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.GoalStatus
import eu.bolh.app.domain.model.Stage
import eu.bolh.app.domain.model.Task
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepository(
    private val dao: GoalDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun today(): LocalDate = LocalDate.now(clock)

    fun observeActiveGoals(): Flow<List<Goal>> =
        dao.observeByStatus(GoalStatus.Active).map { rows -> rows.map { it.toDomain() } }

    fun observeAllGoals(): Flow<List<Goal>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeGoal(id: String): Flow<Goal?> =
        dao.observeById(id).map { it?.toDomain() }

    fun observeStages(): Flow<Map<String, List<Stage>>> =
        dao.observeStages().map { rows ->
            rows.map { it.toDomain() }.groupBy { it.goalId }
        }

    fun observeStagesForGoal(goalId: String): Flow<List<Stage>> =
        dao.observeStagesForGoal(goalId).map { rows -> rows.map { it.toDomain() } }

    fun observeTasks(): Flow<Map<String, List<Task>>> =
        dao.observeTasks().map { rows ->
            rows.map { it.toDomain() }.groupBy { it.stageId }
        }

    /**
     * Завершённые сроки — вход для личного коэффициента (§4.4).
     * Берётся только первая запись по каждой цели: коэффициент считается от того
     * срока, который человек поставил себе изначально, а не от последнего переноса.
     */
    fun observeCompletedDeadlines(): Flow<List<DeadlineRecord>> =
        dao.observeDeadlineRecords().map { rows ->
            rows.groupBy { it.goalId }
                .mapNotNull { (_, records) -> records.minByOrNull { it.sequence } }
                .filter { it.actualEnd != null }
                .map {
                    DeadlineRecord(
                        plannedStart = it.plannedStart,
                        plannedEnd = it.plannedEnd,
                        actualEnd = it.actualEnd!!,
                    )
                }
        }

    suspend fun completedDeadlines(): List<DeadlineRecord> =
        dao.allDeadlineRecords()
            .groupBy { it.goalId }
            .mapNotNull { (_, records) -> records.minByOrNull { it.sequence } }
            .filter { it.actualEnd != null }
            .map {
                DeadlineRecord(
                    plannedStart = it.plannedStart,
                    plannedEnd = it.plannedEnd,
                    actualEnd = it.actualEnd!!,
                )
            }

    /** Все поставленные сроки, включая перенесённые: «из 9 сроков ты уложился в 2». */
    suspend fun allDeadlines(): List<DeadlineRecord> =
        dao.allDeadlineRecords()
            .filter { it.actualEnd != null }
            .map {
                DeadlineRecord(
                    plannedStart = it.plannedStart,
                    plannedEnd = it.plannedEnd,
                    actualEnd = it.actualEnd!!,
                )
            }

    suspend fun create(goal: Goal) {
        val now = clock.millis()
        dao.insert(goal.toEntity(createdAt = now, updatedAt = now))
        if (goal.deadline != null) {
            dao.upsertDeadlineRecord(
                DeadlineRecordEntity(
                    id = UUID.randomUUID().toString(),
                    goalId = goal.id,
                    plannedStart = goal.startDate,
                    plannedEnd = goal.deadline,
                    sequence = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun update(goal: Goal) {
        val existing = dao.findById(goal.id) ?: return
        dao.update(goal.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
    }

    suspend fun delete(goalId: String) {
        dao.softDelete(goalId, clock.millis())
    }

    /**
     * Новый срок для просроченной цели (§4.6). Старая запись остаётся нетронутой:
     * промах — это данные, и он должен уточнить коэффициент, а не исчезнуть.
     */
    suspend fun reschedule(goalId: String, newDeadline: LocalDate) {
        val now = clock.millis()
        val goal = dao.findById(goalId) ?: return
        val records = dao.deadlineRecordsForGoal(goalId)
        dao.setDeadline(goalId, newDeadline, now)
        dao.upsertDeadlineRecord(
            DeadlineRecordEntity(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                plannedStart = goal.startDate,
                plannedEnd = newDeadline,
                wasRescheduled = true,
                sequence = (records.maxOfOrNull { it.sequence } ?: 0) + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun complete(goalId: String) {
        val now = clock.millis()
        val done = today()
        dao.setStatus(goalId, GoalStatus.Done, done, now)
        dao.closeDeadlineRecords(goalId, done, now)
    }

    suspend fun reopen(goalId: String) {
        dao.setStatus(goalId, GoalStatus.Active, null, clock.millis())
    }

    // --- Этапы и задачи ---------------------------------------------------

    /** Возвращает id заведённого этапа: к нему сразу цепляются задачи. */
    suspend fun addStage(goalId: String, name: String, dueDate: LocalDate? = null): String {
        val now = clock.millis()
        val order = dao.stagesForGoal(goalId).size
        val id = UUID.randomUUID().toString()
        dao.upsertStage(
            StageEntity(
                id = id,
                goalId = goalId,
                name = name.trim(),
                dueDate = dueDate,
                sortOrder = order,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun setStageDone(stageId: String, done: Boolean) {
        dao.setStageDone(stageId, if (done) today() else null, clock.millis())
    }

    /**
     * Переименование этапа и задачи.
     *
     * Опечатку исправляют, а не пересоздают цель заново: имя — это подпись
     * к работе, а не сама работа.
     */
    suspend fun renameStage(stageId: String, name: String) {
        if (name.isBlank()) return
        dao.renameStage(stageId, name.trim(), clock.millis())
    }

    suspend fun renameTask(taskId: String, name: String) {
        if (name.isBlank()) return
        dao.renameTask(taskId, name.trim(), clock.millis())
    }

    suspend fun deleteStage(stageId: String) {
        dao.softDeleteStage(stageId, clock.millis())
    }

    suspend fun addTask(stageId: String, name: String) {
        val now = clock.millis()
        dao.upsertTask(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                stageId = stageId,
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun setTaskDone(taskId: String, done: Boolean) {
        dao.setTaskDone(taskId, if (done) today() else null, clock.millis())
    }

    suspend fun deleteTask(taskId: String) {
        dao.softDeleteTask(taskId, clock.millis())
    }
}

private fun GoalEntity.toDomain() = Goal(
    id = id,
    name = name,
    icon = icon,
    startDate = startDate,
    deadline = deadline,
    status = status,
    doneAt = doneAt,
    sortOrder = sortOrder,
)

private fun Goal.toEntity(createdAt: Long, updatedAt: Long) = GoalEntity(
    id = id,
    name = name,
    icon = icon,
    startDate = startDate,
    deadline = deadline,
    status = status,
    doneAt = doneAt,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun StageEntity.toDomain() = Stage(
    id = id,
    goalId = goalId,
    name = name,
    dueDate = dueDate,
    doneAt = doneAt,
    sortOrder = sortOrder,
)

private fun TaskEntity.toDomain() = Task(
    id = id,
    stageId = stageId,
    name = name,
    doneAt = doneAt,
    sortOrder = sortOrder,
)
