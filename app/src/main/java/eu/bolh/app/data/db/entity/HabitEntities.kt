package eu.bolh.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.ScheduleType
import java.time.LocalDate

/**
 * Привычка (§8.2).
 *
 * Схема сразу пригодна к синхронизации: у каждой записи UUID, время создания
 * и изменения, мягкое удаление. Иначе переезд в облако станет переписыванием
 * половины приложения (§8.1).
 */
@Entity(
    tableName = "habits",
    indices = [Index("goalId"), Index("categoryId"), Index("deletedAt")],
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val kind: HabitKind,
    val mode: HabitMode,
    val scheduleType: ScheduleType,
    /** Дни недели битовой маской: понедельник — младший бит. */
    val scheduleDays: Int? = null,
    val timesPerWeek: Int? = null,
    /**
     * Сколько раз за день привычку ждут. Зубы чистят утром и вечером, крем
     * мажут дважды — одна галочка на такой день врёт (§5.5).
     */
    val timesPerDay: Int = 1,
    /** Действие, которое невозможно не сделать: «одна страница». */
    val minimumLabel: String? = null,
    /** То, ради чего привычка заведена: «20 страниц». */
    val normLabel: String? = null,
    val unit: String? = null,
    /** Оценка длительности одного выполнения — из неё считается цена цели в часах. */
    val estimatedMinutes: Int? = null,
    /** Стоимость одного случая вредной привычки, в минорных единицах валюты. */
    val costPerOccurrenceMinor: Long? = null,
    val costCurrencyCode: String? = null,
    val limitPerWeek: Int? = null,
    val skipAllowancePerMonth: Int = 0,
    /** Первое напоминание дня, минуты от полуночи. Остальные лежат в extraReminders. */
    val reminderMinuteOfDay: Int? = null,
    val reminderRepeatEvening: Boolean = false,
    /**
     * Остальные напоминания дня, минуты от полуночи через запятую.
     *
     * Воду пьют трижды в день, и напоминать о ней нужно трижды (§5.6). Первое
     * время осталось в прежней колонке: записи прошлых версий читаются как есть.
     */
    @ColumnInfo(defaultValue = "''")
    val extraReminders: String = "",
    /** Напоминание звучит как будильник: со звуком, пока его не откроют. */
    @ColumnInfo(defaultValue = "0")
    val reminderAlarm: Boolean = false,
    val categoryId: String? = null,
    val goalId: String? = null,
    val startDate: LocalDate,
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Отметка дня. Уровень minimum или norm — день засчитан, slip — срыв вредной привычки.
 *
 * Уникальности по (habitId, date) нет намеренно: у вредной привычки в режиме лимита
 * за день бывает несколько срывов. Одну отметку в день для полезных привычек
 * держит репозиторий.
 */
@Entity(
    tableName = "habit_entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId", "date"), Index("deletedAt")],
)
data class HabitEntryEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: LocalDate,
    val level: EntryLevel,
    /** Сколько сделано в единицах привычки: страниц, минут, километров. */
    val value: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Пауза: дни болезни, отпуска, переезда. Из знаменателя статистики исключаются (§5.7). */
@Entity(
    tableName = "habit_pauses",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId"), Index("deletedAt")],
)
data class HabitPauseEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val reason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
