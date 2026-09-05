package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.Mark
import eu.bolh.app.domain.model.Pause
import eu.bolh.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitAnalyticsTest {

    // 2026-03-02 — понедельник, 2026-03-29 — воскресенье: ровно четыре недели.
    private val windowStart = LocalDate.of(2026, 3, 2)
    private val today = LocalDate.of(2026, 3, 29)
    private val window = DayWindow(windowStart, today)
    private val start = LocalDate.of(2026, 1, 1)
    private val daily = HabitSchedule(ScheduleType.Daily)

    private fun marks(vararg days: Int, level: EntryLevel = EntryLevel.Minimum): List<Mark> =
        days.map { Mark(LocalDate.of(2026, 3, it), level) }

    @Test
    fun `темп это доля выполненных дней от запланированных`() {
        val done = marks(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        val tempo = HabitAnalytics.tempo(done, daily, window, start)
        assertEquals(14.0 / 28.0, tempo!!, 1e-9)
    }

    @Test
    fun `дни паузы уходят из знаменателя а не в пропуски`() {
        // Неделя паузы: 28 дней окна превращаются в 21 активный день.
        val pause = listOf(Pause(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15)))
        val done = marks(2, 3, 4, 5, 6, 7, 8)

        val withoutPause = HabitAnalytics.tempo(done, daily, window, start)
        val withPause = HabitAnalytics.tempo(done, daily, window, start, pause)

        assertEquals(7.0 / 28.0, withoutPause!!, 1e-9)
        assertEquals(7.0 / 21.0, withPause!!, 1e-9)
        assertTrue("пауза не должна ухудшать статистику", withPause > withoutPause)
    }

    @Test
    fun `у расписания по дням недели знаменатель считает только эти дни`() {
        val schedule = HabitSchedule(
            type = ScheduleType.Weekdays,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        // За четыре недели запланировано 12 дней, отмечено 6.
        val done = marks(2, 4, 6, 9, 11, 13)
        assertEquals(0.5, HabitAnalytics.tempo(done, schedule, window, start)!!, 1e-9)
    }

    @Test
    fun `у N раз в неделю знаменатель считается на окно целиком`() {
        val schedule = HabitSchedule(type = ScheduleType.TimesPerWeek, timesPerWeek = 3)
        // 3 раза в неделю × 4 недели = 12 запланированных дней.
        val done = marks(2, 5, 7, 12, 16, 20)
        assertEquals(0.5, HabitAnalytics.tempo(done, schedule, window, start)!!, 1e-9)
    }

    @Test
    fun `перевыполнение не даёт больше единицы`() {
        val schedule = HabitSchedule(type = ScheduleType.TimesPerWeek, timesPerWeek = 1)
        val done = (2..29).map { Mark(LocalDate.of(2026, 3, it), EntryLevel.Norm) }
        assertEquals(1.0, HabitAnalytics.tempo(done, schedule, window, start)!!, 1e-9)
    }

    @Test
    fun `без запланированных дней темпа нет а не ноль`() {
        val schedule = HabitSchedule(type = ScheduleType.Weekdays, days = emptySet())
        assertNull(HabitAnalytics.tempo(emptyList(), schedule, window, start))
    }

    @Test
    fun `привычка младше окна считается только от своего старта`() {
        val startedMidWindow = LocalDate.of(2026, 3, 22)
        val done = marks(22, 23, 24, 25)
        // Активных дней 8 (22-29), из них выполнено 4.
        assertEquals(0.5, HabitAnalytics.tempo(done, daily, window, startedMidWindow)!!, 1e-9)
    }

    @Test
    fun `глубина это доля нормы среди выполненных дней`() {
        val done = marks(2, 3, 4, 5) + marks(6, 7, level = EntryLevel.Norm)
        assertEquals(2.0 / 6.0, HabitAnalytics.depth(done, window, start)!!, 1e-9)
    }

    @Test
    fun `без выполненных дней глубины нет`() {
        assertNull(HabitAnalytics.depth(emptyList(), window, start))
    }

    @Test
    fun `норма перевешивает минимум если за день пришли обе отметки`() {
        val done = listOf(
            Mark(LocalDate.of(2026, 3, 10), EntryLevel.Minimum),
            Mark(LocalDate.of(2026, 3, 10), EntryLevel.Norm),
        )
        assertEquals(1.0, HabitAnalytics.depth(done, window, start)!!, 1e-9)
    }

    @Test
    fun `в игровом режиме пропуск обнуляет серию`() {
        val done = marks(25, 27, 28, 29)
        val streak = HabitAnalytics.streak(HabitMode.Game, done, daily, today, start)
        assertEquals(3, streak)
    }

    @Test
    fun `неотмеченный сегодняшний день серию не рвёт`() {
        val done = marks(24, 25, 26, 27, 28)
        val streak = HabitAnalytics.streak(HabitMode.Game, done, daily, today, start)
        assertEquals(5, streak)
    }

    @Test
    fun `право на пропуск держит счёт пока хватает дней отдыха`() {
        // Пропущены 27, 24 и 23 марта. Двух дней отдыха хватает на два первых пропуска.
        val done = marks(25, 26, 28, 29)
        val streak = HabitAnalytics.streak(
            mode = HabitMode.Skips,
            marks = done,
            schedule = daily,
            today = today,
            startDate = start,
            skipAllowancePerMonth = 2,
        )
        assertEquals(4, streak)
    }

    @Test
    fun `в режиме минимума серии нет`() {
        val done = marks(25, 26, 27, 28, 29)
        assertNull(HabitAnalytics.streak(HabitMode.Minimum, done, daily, today, start))
    }

    @Test
    fun `у N раз в неделю дневной серии нет`() {
        val schedule = HabitSchedule(type = ScheduleType.TimesPerWeek, timesPerWeek = 3)
        assertNull(HabitAnalytics.streak(HabitMode.Game, marks(27, 28, 29), schedule, today, start))
    }

    @Test
    fun `дни паузы серию не рвут и не удлиняют`() {
        val pause = listOf(Pause(LocalDate.of(2026, 3, 26), LocalDate.of(2026, 3, 27)))
        val done = marks(24, 25, 28, 29)
        val streak = HabitAnalytics.streak(HabitMode.Game, done, daily, today, start, pause)
        assertEquals(4, streak)
    }

    @Test
    fun `после двух пропусков подряд включается восстановление`() {
        val done = marks(24, 25, 26, 29)
        assertTrue(HabitAnalytics.needsRecovery(done, daily, today, start))
    }

    @Test
    fun `один пропуск восстановления не включает`() {
        val done = marks(21, 22, 23, 24, 25, 26, 27, 29)
        assertFalse(HabitAnalytics.needsRecovery(done, daily, today, start))
    }

    @Test
    fun `девять дней без срыва`() {
        val slips = listOf(LocalDate.of(2026, 3, 20))
        assertEquals(9, HabitAnalytics.daysWithoutSlip(slips, today, start))
    }

    @Test
    fun `без единого срыва счёт идёт от старта привычки`() {
        val startedAt = LocalDate.of(2026, 3, 1)
        assertEquals(28, HabitAnalytics.daysWithoutSlip(emptyList(), today, startedAt))
    }

    @Test
    fun `срывы за неделю считаются от первого дня недели`() {
        val slips = listOf(
            LocalDate.of(2026, 3, 24),
            LocalDate.of(2026, 3, 26),
            LocalDate.of(2026, 3, 31),
        )
        assertEquals(2, HabitAnalytics.slipsInWeek(slips, LocalDate.of(2026, 3, 23)))
    }

    @Test
    fun `без истории экономия считается по одному случаю в день`() {
        val startedAt = LocalDate.of(2026, 3, 1)
        val saved = HabitAnalytics.avoidedCostMinor(
            slips = emptyList(),
            today = today,
            startDate = startedAt,
            costPerOccurrenceMinor = 500L,
        )
        assertEquals(28 * 500L, saved)
    }

    @Test
    fun `с историей экономия считается по собственной частоте срывов`() {
        // 20 срывов за 78 дней до последнего срыва, 9 чистых дней, 5 евро за случай.
        val slips = (1..20).map { LocalDate.of(2026, 3, 20).minusDays((it * 3).toLong()) } +
            LocalDate.of(2026, 3, 20)
        val saved = HabitAnalytics.avoidedCostMinor(
            slips = slips,
            today = today,
            startDate = start,
            costPerOccurrenceMinor = 500L,
        )
        // rate = 21 / 78, дней без срыва 9 → floor(21/78 × 9 × 500) = 1211
        assertEquals(1211L, saved)
    }
}
