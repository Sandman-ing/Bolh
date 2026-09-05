package eu.bolh.app.domain.model

import java.time.LocalDate

enum class GoalStatus { Active, Done, Archived }

/** Тип копилки (§6.6). Отдельный банковский счёт третьим режимом не является. */
enum class JarMode {
    /** Виртуальный конверт: деньги на обычном счёте, но вычтены из «доступно потратить». */
    Envelope,

    /** Просто цифра: «надо 5000, накоплено 1200», без связи с балансом. */
    Number,
}

/** Пополнение копилки. Без истории пополнений прогноз по копилке считать не из чего. */
data class Contribution(val date: LocalDate, val amountMinor: Long)

/** Цель так, как её видит остальное приложение. */
data class Goal(
    val id: String,
    val name: String,
    val icon: String,
    val startDate: LocalDate,
    val deadline: LocalDate?,
    val status: GoalStatus = GoalStatus.Active,
    val doneAt: LocalDate? = null,
    val sortOrder: Int = 0,
)

/** Этап — обозримый кусок цели. Дата у него необязательна. */
data class Stage(
    val id: String,
    val goalId: String,
    val name: String,
    val dueDate: LocalDate? = null,
    val doneAt: LocalDate? = null,
    val sortOrder: Int = 0,
) {
    val isDone: Boolean get() = doneAt != null
}

/** Задача — чек-лист внутри этапа. */
data class Task(
    val id: String,
    val stageId: String,
    val name: String,
    val doneAt: LocalDate? = null,
    val sortOrder: Int = 0,
) {
    val isDone: Boolean get() = doneAt != null
}

/** Порядок целей на экране выбирает пользователь (§4.7). */
enum class GoalSort { Manual, Deadline, Tempo }

/** Что известно о цели на сегодня — вход для прогноза (§4.3). */
data class GoalSnapshot(
    val startDate: LocalDate,
    val today: LocalDate,
    val deadline: LocalDate?,
    val stagesTotal: Int,
    val stagesClosed: Int,
    val jarTargetMinor: Long? = null,
    val jarCurrentMinor: Long? = null,
    val contributions: List<Contribution> = emptyList(),
)

/** Откуда взялась прогнозная дата. Нужно, чтобы объяснить число человеку. */
enum class ForecastSource { Stages, Jar, Both }

data class GoalForecast(
    val date: LocalDate,
    val source: ForecastSource,
    /** Положительное — позже срока, отрицательное — с опережением, null — срока нет. */
    val monthsVsDeadline: Long?,
)

/** Один поставленный и закрытый срок. Источник личного коэффициента (§4.4). */
data class DeadlineRecord(
    val plannedStart: LocalDate,
    val plannedEnd: LocalDate,
    val actualEnd: LocalDate,
)

/** Привязанная к цели привычка — в той мере, в какой она стоит времени (§4.5). */
data class LinkedHabitCost(
    val schedule: HabitSchedule,
    val estimatedMinutes: Int?,
)
