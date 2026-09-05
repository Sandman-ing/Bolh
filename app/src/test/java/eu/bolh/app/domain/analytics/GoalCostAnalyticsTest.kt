package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.LinkedHabitCost
import eu.bolh.app.domain.model.ScheduleType
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalCostAnalyticsTest {

    @Test
    fun `время в неделю складывается по привязанным привычкам`() {
        val habits = listOf(
            LinkedHabitCost(HabitSchedule(ScheduleType.Daily), estimatedMinutes = 20),
            LinkedHabitCost(
                HabitSchedule(
                    type = ScheduleType.Weekdays,
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                ),
                estimatedMinutes = 45,
            ),
        )
        // 7 × 20 + 3 × 45 = 275 минут в неделю.
        assertEquals(275, GoalCostAnalytics.minutesPerWeek(habits))
    }

    @Test
    fun `привычки без оценки в сумму не входят`() {
        val habits = listOf(
            LinkedHabitCost(HabitSchedule(ScheduleType.Daily), estimatedMinutes = 20),
            LinkedHabitCost(HabitSchedule(ScheduleType.Daily), estimatedMinutes = null),
        )
        assertEquals(140, GoalCostAnalytics.minutesPerWeek(habits))
        assertEquals(1, GoalCostAnalytics.habitsWithoutEstimate(habits))
    }

    @Test
    fun `без единой оценки времени цены нет а не ноль`() {
        val habits = listOf(LinkedHabitCost(HabitSchedule(ScheduleType.Daily), estimatedMinutes = null))
        assertNull(GoalCostAnalytics.minutesPerWeek(habits))
    }

    @Test
    fun `деньги в месяц это пополнение плюс треть трат за три месяца`() {
        assertEquals(
            50_000L,
            GoalCostAnalytics.moneyPerMonthMinor(
                plannedMonthlyMinor = 20_000L,
                spendLastThreeMonthsMinor = 90_000L,
            ),
        )
    }
}
