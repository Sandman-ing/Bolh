package eu.bolh.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Полезная привычка — цель в том, чтобы делать. Вредная — чтобы не делать (§5.1). */
enum class HabitKind { Good, Bad }

/**
 * Один механизм с разным поведением при пропуске (§5.2, §5.4).
 * В интерфейсе режимы подписаны поведением, а не термином.
 */
enum class HabitMode {
    /** Серия обнуляется при пропуске. */
    Game,

    /** Два дня отдыха в месяц не ломают счёт. */
    Skips,

    /** Плохой день не обнуляет прогресс: минимум и норма. */
    Minimum,

    /** Вредная: дней без срыва. */
    Counter,

    /** Вредная: не больше N раз в неделю. */
    Limit,
}

enum class ScheduleType { Daily, Weekdays, TimesPerWeek }

/** Отметка дня. Slip — срыв вредной привычки. */
enum class EntryLevel { Minimum, Norm, Slip }

data class HabitSchedule(
    val type: ScheduleType,
    val days: Set<DayOfWeek> = emptySet(),
    val timesPerWeek: Int? = null,
) {
    /** Сколько раз в неделю привычка запланирована. Нужно для цены цели в часах (§4.5). */
    val perWeek: Double
        get() = when (type) {
            ScheduleType.Daily -> 7.0
            ScheduleType.Weekdays -> days.size.toDouble()
            ScheduleType.TimesPerWeek -> (timesPerWeek ?: 0).toDouble()
        }

    fun plannedOn(date: LocalDate): Boolean = when (type) {
        ScheduleType.Daily -> true
        ScheduleType.Weekdays -> date.dayOfWeek in days
        // У «N раз в неделю» нет конкретных дней: план считается на окно целиком.
        ScheduleType.TimesPerWeek -> false
    }
}

/** Привычка так, как её видит остальное приложение: без полей хранения и меток времени. */
data class Habit(
    val id: String,
    val name: String,
    val icon: String,
    val kind: HabitKind,
    val mode: HabitMode,
    val schedule: HabitSchedule,
    val minimumLabel: String? = null,
    val normLabel: String? = null,
    val unit: String? = null,
    val estimatedMinutes: Int? = null,
    val costPerOccurrenceMinor: Long? = null,
    val costCurrencyCode: String? = null,
    val limitPerWeek: Int? = null,
    val skipAllowancePerMonth: Int = 0,
    /** Сколько раз за день привычку ждут. Один — обычный случай. */
    val timesPerDay: Int = 1,
    /** Времена напоминаний, минуты от полуночи, по возрастанию. Пусто — не напоминать. */
    val reminderTimes: List<Int> = emptyList(),
    val reminderRepeatEvening: Boolean = false,
    /** Напоминать как будильник: громко и до тех пор, пока не откроют. */
    val reminderAlarm: Boolean = false,
    val categoryId: String? = null,
    val goalId: String? = null,
    val startDate: LocalDate,
    val sortOrder: Int = 0,
)

/** Пауза: дни болезни, отпуска, переезда. Из знаменателя статистики исключаются (§5.7). */
data class Pause(val from: LocalDate, val to: LocalDate) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

data class Mark(val date: LocalDate, val level: EntryLevel)

/** Отрезок дат, включая обе границы. */
data class DayWindow(val from: LocalDate, val to: LocalDate) {

    fun dates(): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<LocalDate>()
        var date = from
        while (!date.isAfter(to)) {
            out += date
            date = date.plusDays(1)
        }
        return out
    }

    fun covers(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    companion object {
        fun lastDays(today: LocalDate, days: Long): DayWindow =
            DayWindow(today.minusDays(days - 1), today)
    }
}
