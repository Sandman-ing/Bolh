package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Когда звучат напоминания (§5.6, §6.5).
 *
 * Будильник системы одноразовый, и следующий момент считается здесь. Ошибка
 * на минуту означает напоминание, которое приходит дважды или не приходит.
 */
class ReminderTimesTest {

    // Среда, 16 сентября 2026.
    private val wednesday = LocalDate.of(2026, 9, 16)
    private val daily = HabitSchedule(ScheduleType.Daily)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))

    @Test
    fun `время ещё впереди — сегодня`() {
        val next = ReminderTimes.nextHabitFire(at(wednesday, 8), 9 * 60, daily, wednesday.minusDays(10))
        assertEquals(at(wednesday, 9), next)
    }

    @Test
    fun `время уже прошло — завтра`() {
        val next = ReminderTimes.nextHabitFire(at(wednesday, 9, 30), 9 * 60, daily, wednesday.minusDays(10))
        assertEquals(at(wednesday.plusDays(1), 9), next)
    }

    @Test
    fun `ровно в момент срабатывания — следующий раз завтра`() {
        // Получатель будильника ставит следующий сразу после срабатывания:
        // тот же момент вернул бы будильник в прошлое, и он звенел бы по кругу.
        val next = ReminderTimes.nextHabitFire(at(wednesday, 9), 9 * 60, daily, wednesday.minusDays(10))
        assertEquals(at(wednesday.plusDays(1), 9), next)
    }

    @Test
    fun `до начала привычки не напоминаем`() {
        val start = wednesday.plusDays(5)
        val next = ReminderTimes.nextHabitFire(at(wednesday, 8), 9 * 60, daily, start)
        assertEquals(at(start, 9), next)
    }

    @Test
    fun `по дням недели — пропускаются дни, когда привычку не ждут`() {
        val mondayFriday = HabitSchedule(ScheduleType.Weekdays, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val next = ReminderTimes.nextHabitFire(at(wednesday, 8), 18 * 60, mondayFriday, wednesday.minusDays(10))
        assertEquals(at(LocalDate.of(2026, 9, 18), 18), next)
    }

    @Test
    fun `по дням недели — в пятницу вечером следующий раз в понедельник`() {
        val friday = LocalDate.of(2026, 9, 18)
        val mondayFriday = HabitSchedule(ScheduleType.Weekdays, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val next = ReminderTimes.nextHabitFire(at(friday, 20), 18 * 60, mondayFriday, wednesday.minusDays(10))
        assertEquals(at(LocalDate.of(2026, 9, 21), 18), next)
    }

    @Test
    fun `несколько раз в неделю — напоминание каждый день`() {
        val times = HabitSchedule(ScheduleType.TimesPerWeek, timesPerWeek = 3)
        val next = ReminderTimes.nextHabitFire(at(wednesday, 21), 20 * 60, times, wednesday.minusDays(10))
        assertEquals(at(wednesday.plusDays(1), 20), next)
    }

    @Test
    fun `платёж — первое напоминание за указанные дни`() {
        val payDay = wednesday.plusDays(10)
        val next = ReminderTimes.nextPaymentFire(at(wednesday, 8), payDay, daysBefore = 2)
        assertEquals(LocalDateTime.of(payDay.minusDays(2), ReminderTimes.PAYMENT_TIME), next)
    }

    @Test
    fun `платёж — после первого напоминания следующее в ближайшие 11 00`() {
        val payDay = wednesday.plusDays(1)
        val next = ReminderTimes.nextPaymentFire(at(wednesday, 12), payDay, daysBefore = 2)
        assertEquals(LocalDateTime.of(payDay, ReminderTimes.PAYMENT_TIME), next)
    }

    @Test
    fun `платёж — после дня списания напоминать не о чем`() {
        val next = ReminderTimes.nextPaymentFire(at(wednesday, 12), wednesday, daysBefore = 1)
        assertNull(next)
    }

    @Test
    fun `платёж — отрицательный запас считается нулём`() {
        val payDay = wednesday.plusDays(3)
        val next = ReminderTimes.nextPaymentFire(at(wednesday, 8), payDay, daysBefore = -5)
        assertEquals(LocalDateTime.of(payDay, ReminderTimes.PAYMENT_TIME), next)
    }
}
