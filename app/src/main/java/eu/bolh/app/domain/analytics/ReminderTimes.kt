package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.ScheduleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Когда звучать напоминанию (§5.6, §6.5). Чистый расчёт без Android: его
 * проверяют тесты, а будильник системы только исполняет ответ.
 */
object ReminderTimes {

    /** Время, когда приходит напоминание о платеже. */
    val PAYMENT_TIME: LocalTime = LocalTime.of(11, 0)

    /**
     * Ближайший момент строго после [now], когда привычку ждут в [minuteOfDay].
     *
     * До даты начала привычки не напоминаем: её тогда ещё не было. У привычки
     * по дням недели пропускаются дни, в которые её не ждут. «N раз в неделю»
     * к дням не привязана, поэтому напоминание приходит каждый день.
     */
    fun nextHabitFire(
        now: LocalDateTime,
        minuteOfDay: Int,
        schedule: HabitSchedule,
        startDate: LocalDate,
    ): LocalDateTime {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var date = now.toLocalDate()
        if (!now.toLocalTime().isBefore(time)) date = date.plusDays(1)
        if (date.isBefore(startDate)) date = startDate
        if (schedule.type == ScheduleType.Weekdays && schedule.days.isNotEmpty()) {
            var guard = 0
            while (!schedule.plannedOn(date) && guard < DAYS_IN_WEEK) {
                date = date.plusDays(1)
                guard++
            }
        }
        return LocalDateTime.of(date, time)
    }

    /**
     * Когда напомнить о платеже.
     *
     * Первый раз — за [daysBefore] дней до списания. Если это время уже прошло,
     * а день платежа ещё не наступил, — в ближайшие [PAYMENT_TIME]. После дня
     * платежа напоминать не о чем: пусто.
     */
    fun nextPaymentFire(now: LocalDateTime, paymentDate: LocalDate, daysBefore: Int): LocalDateTime? {
        val first = LocalDateTime.of(paymentDate.minusDays(daysBefore.coerceAtLeast(0).toLong()), PAYMENT_TIME)
        if (first.isAfter(now)) return first
        var next = LocalDateTime.of(now.toLocalDate(), PAYMENT_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.takeIf { !it.toLocalDate().isAfter(paymentDate) }
    }

    private const val DAYS_IN_WEEK = 7
}
