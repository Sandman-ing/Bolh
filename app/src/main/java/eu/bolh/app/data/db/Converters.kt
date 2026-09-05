package eu.bolh.app.data.db

import androidx.room.TypeConverter
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.GoalStatus
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.JarMode
import eu.bolh.app.domain.model.ScheduleType
import java.time.LocalDate

/**
 * Даты хранятся строкой ISO: она сортируется как текст, читается человеком
 * в дампе базы и не зависит от часового пояса. Метки времени — миллисекунды UTC.
 */
class Converters {

    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromHabitKind(value: HabitKind): String = value.name

    @TypeConverter
    fun toHabitKind(value: String): HabitKind = HabitKind.valueOf(value)

    @TypeConverter
    fun fromHabitMode(value: HabitMode): String = value.name

    @TypeConverter
    fun toHabitMode(value: String): HabitMode = HabitMode.valueOf(value)

    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun fromEntryLevel(value: EntryLevel): String = value.name

    @TypeConverter
    fun toEntryLevel(value: String): EntryLevel = EntryLevel.valueOf(value)

    @TypeConverter
    fun fromGoalStatus(value: GoalStatus): String = value.name

    @TypeConverter
    fun toGoalStatus(value: String): GoalStatus = GoalStatus.valueOf(value)

    @TypeConverter
    fun fromJarMode(value: JarMode): String = value.name

    @TypeConverter
    fun toJarMode(value: String): JarMode = JarMode.valueOf(value)
}
