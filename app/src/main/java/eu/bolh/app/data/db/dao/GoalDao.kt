package eu.bolh.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import eu.bolh.app.data.db.entity.DeadlineRecordEntity
import eu.bolh.app.data.db.entity.GoalEntity
import eu.bolh.app.data.db.entity.StageEntity
import eu.bolh.app.data.db.entity.TaskEntity
import eu.bolh.app.domain.model.GoalStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE deletedAt IS NULL AND status = :status ORDER BY sortOrder, createdAt")
    fun observeByStatus(status: GoalStatus): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: GoalEntity)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE goals SET status = :status, doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: GoalStatus, doneAt: LocalDate?, now: Long)

    @Query("UPDATE goals SET deadline = :deadline, updatedAt = :now WHERE id = :id")
    suspend fun setDeadline(id: String, deadline: LocalDate?, now: Long)

    // --- Этапы ------------------------------------------------------------

    @Query("SELECT * FROM stages WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeStages(): Flow<List<StageEntity>>

    @Query("SELECT * FROM stages WHERE goalId = :goalId AND deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeStagesForGoal(goalId: String): Flow<List<StageEntity>>

    @Query("SELECT * FROM stages WHERE goalId = :goalId AND deletedAt IS NULL")
    suspend fun stagesForGoal(goalId: String): List<StageEntity>

    @Upsert
    suspend fun upsertStage(stage: StageEntity)

    @Query("UPDATE stages SET doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setStageDone(id: String, doneAt: LocalDate?, now: Long)

    @Query("UPDATE stages SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteStage(id: String, now: Long)

    // --- Задачи -----------------------------------------------------------

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE stageId = :stageId AND deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeTasksForStage(stageId: String): Flow<List<TaskEntity>>

    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    @Query("UPDATE tasks SET doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setTaskDone(id: String, doneAt: LocalDate?, now: Long)

    @Query("UPDATE tasks SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTask(id: String, now: Long)

    // --- История сроков ---------------------------------------------------

    @Query("SELECT * FROM deadline_records WHERE deletedAt IS NULL ORDER BY sequence")
    suspend fun allDeadlineRecords(): List<DeadlineRecordEntity>

    @Query("SELECT * FROM deadline_records WHERE deletedAt IS NULL ORDER BY sequence")
    fun observeDeadlineRecords(): Flow<List<DeadlineRecordEntity>>

    @Query("SELECT * FROM deadline_records WHERE goalId = :goalId AND deletedAt IS NULL ORDER BY sequence")
    suspend fun deadlineRecordsForGoal(goalId: String): List<DeadlineRecordEntity>

    @Upsert
    suspend fun upsertDeadlineRecord(record: DeadlineRecordEntity)

    @Query("UPDATE deadline_records SET actualEnd = :actualEnd, updatedAt = :now WHERE goalId = :goalId")
    suspend fun closeDeadlineRecords(goalId: String, actualEnd: LocalDate, now: Long)
}
