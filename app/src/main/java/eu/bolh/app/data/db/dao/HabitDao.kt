package eu.bolh.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import eu.bolh.app.data.db.entity.HabitEntity
import eu.bolh.app.data.db.entity.HabitEntryEntity
import eu.bolh.app.data.db.entity.HabitPauseEntity
import eu.bolh.app.domain.model.HabitKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Мягкое удаление: строки не исчезают, а получают [HabitEntity.deletedAt].
 * Каждый запрос обязан это учитывать — иначе удалённое всплывёт на экране.
 */
@Dao
interface HabitDao {

    @Query(
        """
        SELECT * FROM habits
        WHERE deletedAt IS NULL AND archivedAt IS NULL AND kind = :kind
        ORDER BY sortOrder, createdAt
        """,
    )
    fun observeByKind(kind: HabitKind): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE goalId = :goalId AND deletedAt IS NULL AND archivedAt IS NULL")
    suspend fun findByGoal(goalId: String): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE habits SET archivedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    // --- Отметки ----------------------------------------------------------

    @Query(
        """
        SELECT * FROM habit_entries
        WHERE deletedAt IS NULL AND date BETWEEN :from AND :to
        ORDER BY date
        """,
    )
    fun observeEntriesBetween(from: LocalDate, to: LocalDate): Flow<List<HabitEntryEntity>>

    @Query(
        """
        SELECT * FROM habit_entries
        WHERE habitId = :habitId AND deletedAt IS NULL AND date BETWEEN :from AND :to
        ORDER BY date
        """,
    )
    fun observeEntriesForHabit(
        habitId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<HabitEntryEntity>>

    @Query(
        """
        SELECT * FROM habit_entries
        WHERE habitId = :habitId AND deletedAt IS NULL AND date BETWEEN :from AND :to
        ORDER BY date
        """,
    )
    suspend fun entriesForHabit(
        habitId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<HabitEntryEntity>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date AND deletedAt IS NULL")
    suspend fun entriesOn(habitId: String, date: LocalDate): List<HabitEntryEntity>

    @Upsert
    suspend fun upsertEntry(entry: HabitEntryEntity)

    @Query("UPDATE habit_entries SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteEntry(id: String, now: Long)

    /**
     * Полезная привычка отмечается один раз в день: повторное нажатие меняет
     * уровень, а не плодит записи. У вредной привычки в режиме лимита срывов
     * за день может быть несколько — там используется [upsertEntry] напрямую.
     */
    @Transaction
    suspend fun replaceDayEntry(entry: HabitEntryEntity, now: Long) {
        entriesOn(entry.habitId, entry.date).forEach { softDeleteEntry(it.id, now) }
        upsertEntry(entry)
    }

    // --- Паузы ------------------------------------------------------------

    @Query("SELECT * FROM habit_pauses WHERE habitId = :habitId AND deletedAt IS NULL")
    suspend fun pausesForHabit(habitId: String): List<HabitPauseEntity>

    @Query("SELECT * FROM habit_pauses WHERE deletedAt IS NULL")
    fun observePauses(): Flow<List<HabitPauseEntity>>

    @Upsert
    suspend fun upsertPause(pause: HabitPauseEntity)

    @Query("UPDATE habit_pauses SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeletePause(id: String, now: Long)
}
