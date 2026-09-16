package eu.bolh.app.data.repository

import eu.bolh.app.data.db.dao.HabitDao
import eu.bolh.app.data.db.entity.HabitEntryEntity
import eu.bolh.app.data.db.entity.HabitPauseEntity
import eu.bolh.app.data.mapper.toDomain
import eu.bolh.app.data.mapper.toEntity
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.Mark
import eu.bolh.app.domain.model.Pause
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Хранилище привычек. Вся работа с временем идёт через [clock], чтобы тесты
 * не зависели от того, в какой день их запустили.
 */
class HabitRepository(
    private val dao: HabitDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun today(): LocalDate = LocalDate.now(clock)

    fun observeHabits(kind: HabitKind): Flow<List<Habit>> =
        dao.observeByKind(kind).map { rows -> rows.map { it.toDomain() } }

    fun observeAllHabits(): Flow<List<Habit>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeHabit(id: String): Flow<Habit?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun findHabit(id: String): Habit? = dao.findById(id)?.toDomain()

    /** Отмечен ли день хоть чем-то. Срыв отметкой выполнения не считается. */
    suspend fun isMarked(habitId: String, date: LocalDate): Boolean =
        dao.entriesOn(habitId, date).any { it.level != EntryLevel.Slip }

    /** Сколько отметок выполнения стоит на день: у привычки «трижды в день» их три. */
    suspend fun doneMarksOn(habitId: String, date: LocalDate): Int =
        dao.entriesOn(habitId, date).count { it.level != EntryLevel.Slip }

    /** Все действующие привычки разом — для перестановки напоминаний. */
    suspend fun allHabits(): List<Habit> = observeAllHabits().first()

    /** Отметки за окно, разложенные по привычкам. */
    fun observeMarks(from: LocalDate, to: LocalDate): Flow<Map<String, List<Mark>>> =
        dao.observeEntriesBetween(from, to).map { rows ->
            rows.groupBy(
                keySelector = { it.habitId },
                valueTransform = { Mark(date = it.date, level = it.level) },
            )
        }

    fun observePauses(): Flow<Map<String, List<Pause>>> =
        dao.observePauses().map { rows ->
            rows.groupBy(
                keySelector = { it.habitId },
                valueTransform = { it.toDomain() },
            )
        }

    suspend fun create(habit: Habit) {
        val now = clock.millis()
        dao.insert(habit.toEntity(createdAt = now, updatedAt = now))
    }

    suspend fun update(habit: Habit) {
        val existing = dao.findById(habit.id) ?: return
        dao.update(
            habit.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()),
        )
    }

    suspend fun delete(habitId: String) {
        dao.softDelete(habitId, clock.millis())
    }

    suspend fun archive(habitId: String) {
        dao.archive(habitId, clock.millis())
    }

    /**
     * Отметить день у полезной привычки. Повторное нажатие тем же уровнем снимает
     * отметку: человек должен иметь возможность исправить ошибку в один тап,
     * а не искать меню.
     */
    suspend fun markDay(
        habitId: String,
        date: LocalDate,
        level: EntryLevel,
        value: Double? = null,
    ) {
        val now = clock.millis()
        val existing = dao.entriesOn(habitId, date)
        val timesPerDay = dao.findById(habitId)?.timesPerDay ?: 1

        // Привычка, которую ждут несколько раз за день, набирает отметки по
        // одной. Когда набрано всё — следующее нажатие снимает день целиком:
        // иначе отменить лишнюю отметку было бы нечем (§5.5).
        if (timesPerDay > 1) {
            if (existing.size >= timesPerDay) {
                existing.forEach { dao.softDeleteEntry(it.id, now) }
            } else {
                dao.upsertEntry(
                    HabitEntryEntity(
                        id = UUID.randomUUID().toString(),
                        habitId = habitId,
                        date = date,
                        level = level,
                        value = value,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            return
        }

        if (existing.size == 1 && existing.first().level == level && value == null) {
            dao.softDeleteEntry(existing.first().id, now)
            return
        }
        dao.replaceDayEntry(
            entry = HabitEntryEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                date = date,
                level = level,
                value = value,
                createdAt = now,
                updatedAt = now,
            ),
            now = now,
        )
    }

    /** Срыв вредной привычки. За день их может быть несколько — каждый пишется отдельно. */
    suspend fun addSlip(habitId: String, date: LocalDate) {
        val now = clock.millis()
        dao.upsertEntry(
            HabitEntryEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                date = date,
                level = EntryLevel.Slip,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Отменить последний срыв за день: нажали не глядя — вернули как было. */
    suspend fun undoLastSlip(habitId: String, date: LocalDate) {
        val now = clock.millis()
        dao.entriesOn(habitId, date)
            .filter { it.level == EntryLevel.Slip }
            .maxByOrNull { it.createdAt }
            ?.let { dao.softDeleteEntry(it.id, now) }
    }

    /**
     * Пауза: болезнь, отпуск, переезд. Дни паузы уходят из знаменателя статистики,
     * а не считаются пропусками (§5.7) — поэтому это отдельная запись, а не отметки.
     */
    suspend fun addPause(habitId: String, from: LocalDate, to: LocalDate, reason: String? = null) {
        val now = clock.millis()
        dao.upsertPause(
            HabitPauseEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                fromDate = from,
                toDate = to,
                reason = reason,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun pausesFor(habitId: String): List<Pause> =
        dao.pausesForHabit(habitId).map { it.toDomain() }

    /** Снять паузу целиком: человек вернулся раньше, чем собирался. */
    suspend fun clearPauses(habitId: String) {
        val now = clock.millis()
        dao.pausesForHabit(habitId).forEach { dao.softDeletePause(it.id, now) }
    }

    suspend fun clearDay(habitId: String, date: LocalDate) {
        val now = clock.millis()
        dao.entriesOn(habitId, date).forEach { dao.softDeleteEntry(it.id, now) }
    }
}
