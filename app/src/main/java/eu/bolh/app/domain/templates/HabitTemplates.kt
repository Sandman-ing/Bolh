package eu.bolh.app.domain.templates

import androidx.annotation.StringRes
import eu.bolh.app.R
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.ScheduleType
import java.time.DayOfWeek

/**
 * Готовая привычка в один тап (§5.9).
 *
 * Люди отваливаются не на отметке, а на создании: восемь решений ради «пить воду»
 * убивают продукт. Поэтому в шаблоне заполнено всё, включая разумные минимум и норму.
 */
data class HabitTemplate(
    val key: String,
    @param:StringRes val nameRes: Int,
    val icon: String,
    val kind: HabitKind,
    val mode: HabitMode,
    val schedule: HabitSchedule,
    @param:StringRes val minimumRes: Int? = null,
    @param:StringRes val normRes: Int? = null,
    val estimatedMinutes: Int? = null,
    val limitPerWeek: Int? = null,
)

object HabitTemplates {

    val all: List<HabitTemplate> = listOf(
        HabitTemplate(
            key = "wake",
            nameRes = R.string.tpl_wake,
            icon = "wb_sunny",
            kind = HabitKind.Good,
            mode = HabitMode.Game,
            schedule = HabitSchedule(ScheduleType.Daily),
            normRes = R.string.tpl_wake_norm,
        ),
        HabitTemplate(
            key = "reading",
            nameRes = R.string.tpl_reading,
            icon = "menu_book",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(ScheduleType.Daily),
            minimumRes = R.string.tpl_reading_min,
            normRes = R.string.tpl_reading_norm,
            estimatedMinutes = 25,
        ),
        HabitTemplate(
            key = "gym",
            nameRes = R.string.tpl_gym,
            icon = "fitness_center",
            kind = HabitKind.Good,
            mode = HabitMode.Skips,
            schedule = HabitSchedule(
                type = ScheduleType.Weekdays,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
            normRes = R.string.tpl_gym_norm,
            estimatedMinutes = 60,
        ),
        HabitTemplate(
            key = "run",
            nameRes = R.string.tpl_run,
            icon = "directions_run",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(type = ScheduleType.TimesPerWeek, timesPerWeek = 3),
            minimumRes = R.string.tpl_run_min,
            normRes = R.string.tpl_run_norm,
            estimatedMinutes = 30,
        ),
        HabitTemplate(
            key = "water",
            nameRes = R.string.tpl_water,
            icon = "water_drop",
            kind = HabitKind.Good,
            mode = HabitMode.Game,
            schedule = HabitSchedule(ScheduleType.Daily),
            normRes = R.string.tpl_water_norm,
        ),
        HabitTemplate(
            key = "language",
            nameRes = R.string.tpl_language,
            icon = "translate",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(ScheduleType.Daily),
            minimumRes = R.string.tpl_language_min,
            normRes = R.string.tpl_language_norm,
            estimatedMinutes = 30,
        ),
        HabitTemplate(
            key = "meditation",
            nameRes = R.string.tpl_meditation,
            icon = "self_improvement",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(ScheduleType.Daily),
            minimumRes = R.string.tpl_meditation_min,
            normRes = R.string.tpl_meditation_norm,
            estimatedMinutes = 15,
        ),
        HabitTemplate(
            key = "steps",
            nameRes = R.string.tpl_steps,
            icon = "directions_walk",
            kind = HabitKind.Good,
            mode = HabitMode.Skips,
            schedule = HabitSchedule(ScheduleType.Daily),
            normRes = R.string.tpl_steps_norm,
            estimatedMinutes = 40,
        ),
        HabitTemplate(
            key = "vitamins",
            nameRes = R.string.tpl_vitamins,
            icon = "medication",
            kind = HabitKind.Good,
            mode = HabitMode.Game,
            schedule = HabitSchedule(ScheduleType.Daily),
            normRes = R.string.tpl_vitamins_norm,
        ),
        HabitTemplate(
            key = "journal",
            nameRes = R.string.tpl_journal,
            icon = "edit_note",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(ScheduleType.Daily),
            minimumRes = R.string.tpl_journal_min,
            normRes = R.string.tpl_journal_norm,
            estimatedMinutes = 10,
        ),
        HabitTemplate(
            key = "sleep",
            nameRes = R.string.tpl_sleep,
            icon = "bedtime",
            kind = HabitKind.Good,
            mode = HabitMode.Game,
            schedule = HabitSchedule(ScheduleType.Daily),
            normRes = R.string.tpl_sleep_norm,
        ),
        HabitTemplate(
            key = "tidy",
            nameRes = R.string.tpl_tidy,
            icon = "cleaning_services",
            kind = HabitKind.Good,
            mode = HabitMode.Skips,
            schedule = HabitSchedule(type = ScheduleType.TimesPerWeek, timesPerWeek = 2),
            normRes = R.string.tpl_tidy_norm,
            estimatedMinutes = 20,
        ),
        HabitTemplate(
            key = "instrument",
            nameRes = R.string.tpl_instrument,
            icon = "music_note",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(ScheduleType.Daily),
            minimumRes = R.string.tpl_instrument_min,
            normRes = R.string.tpl_instrument_norm,
            estimatedMinutes = 45,
        ),
        HabitTemplate(
            key = "study",
            nameRes = R.string.tpl_study,
            icon = "code",
            kind = HabitKind.Good,
            mode = HabitMode.Minimum,
            schedule = HabitSchedule(
                type = ScheduleType.Weekdays,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            ),
            minimumRes = R.string.tpl_study_min,
            normRes = R.string.tpl_study_norm,
            estimatedMinutes = 60,
        ),
        HabitTemplate(
            key = "smoking",
            nameRes = R.string.tpl_smoking,
            icon = "smoking_rooms",
            kind = HabitKind.Bad,
            mode = HabitMode.Counter,
            schedule = HabitSchedule(ScheduleType.Daily),
        ),
        HabitTemplate(
            key = "alcohol",
            nameRes = R.string.tpl_alcohol,
            icon = "local_bar",
            kind = HabitKind.Bad,
            mode = HabitMode.Limit,
            schedule = HabitSchedule(ScheduleType.Daily),
            limitPerWeek = 2,
        ),
        HabitTemplate(
            key = "delivery",
            nameRes = R.string.tpl_delivery,
            icon = "fastfood",
            kind = HabitKind.Bad,
            mode = HabitMode.Limit,
            schedule = HabitSchedule(ScheduleType.Daily),
            limitPerWeek = 1,
        ),
        HabitTemplate(
            key = "sweets",
            nameRes = R.string.tpl_sweets,
            icon = "cookie",
            kind = HabitKind.Bad,
            mode = HabitMode.Limit,
            schedule = HabitSchedule(ScheduleType.Daily),
            limitPerWeek = 3,
        ),
        HabitTemplate(
            key = "scrolling",
            nameRes = R.string.tpl_scrolling,
            icon = "smartphone",
            kind = HabitKind.Bad,
            mode = HabitMode.Counter,
            schedule = HabitSchedule(ScheduleType.Daily),
        )
    )

    val good: List<HabitTemplate> get() = all.filter { it.kind == HabitKind.Good }

    val bad: List<HabitTemplate> get() = all.filter { it.kind == HabitKind.Bad }
}
