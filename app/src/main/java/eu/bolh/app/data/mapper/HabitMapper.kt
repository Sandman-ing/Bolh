package eu.bolh.app.data.mapper

import eu.bolh.app.data.db.entity.HabitEntity
import eu.bolh.app.data.db.entity.HabitPauseEntity
import eu.bolh.app.domain.model.Habit
import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.Pause
import java.time.DayOfWeek

/** Понедельник — младший бит, воскресенье — старший. */
fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int?.toDayOfWeekSet(): Set<DayOfWeek> {
    val mask = this ?: return emptySet()
    return DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and (1 shl (it.value - 1)) != 0 }
}

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    icon = icon,
    kind = kind,
    mode = mode,
    schedule = HabitSchedule(
        type = scheduleType,
        days = scheduleDays.toDayOfWeekSet(),
        timesPerWeek = timesPerWeek,
    ),
    minimumLabel = minimumLabel,
    normLabel = normLabel,
    unit = unit,
    estimatedMinutes = estimatedMinutes,
    costPerOccurrenceMinor = costPerOccurrenceMinor,
    costCurrencyCode = costCurrencyCode,
    limitPerWeek = limitPerWeek,
    skipAllowancePerMonth = skipAllowancePerMonth,
    timesPerDay = timesPerDay,
    reminderMinuteOfDay = reminderMinuteOfDay,
    reminderRepeatEvening = reminderRepeatEvening,
    categoryId = categoryId,
    goalId = goalId,
    startDate = startDate,
    sortOrder = sortOrder,
)

fun Habit.toEntity(createdAt: Long, updatedAt: Long, deletedAt: Long? = null): HabitEntity = HabitEntity(
    id = id,
    name = name,
    icon = icon,
    kind = kind,
    mode = mode,
    scheduleType = schedule.type,
    scheduleDays = schedule.days.takeIf { it.isNotEmpty() }?.toMask(),
    timesPerWeek = schedule.timesPerWeek,
    minimumLabel = minimumLabel,
    normLabel = normLabel,
    unit = unit,
    estimatedMinutes = estimatedMinutes,
    costPerOccurrenceMinor = costPerOccurrenceMinor,
    costCurrencyCode = costCurrencyCode,
    limitPerWeek = limitPerWeek,
    skipAllowancePerMonth = skipAllowancePerMonth,
    timesPerDay = timesPerDay,
    reminderMinuteOfDay = reminderMinuteOfDay,
    reminderRepeatEvening = reminderRepeatEvening,
    categoryId = categoryId,
    goalId = goalId,
    startDate = startDate,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun HabitPauseEntity.toDomain(): Pause = Pause(from = fromDate, to = toDate)
