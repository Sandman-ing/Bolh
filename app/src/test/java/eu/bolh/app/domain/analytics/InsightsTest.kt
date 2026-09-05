package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.HabitKind
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {

    @Test
    fun `выросшая категория и сорванная привычка дают межблоковый вывод`() {
        val insights = Insights.of(
            InsightInput(
                habits = listOf(
                    HabitFacts(
                        habitId = "delivery-habit",
                        kind = HabitKind.Bad,
                        doneDays = 9,
                        plannedDays = 30,
                        normDays = 0,
                        minimumDays = 0,
                    ),
                ),
                categories = listOf(
                    CategoryFacts(
                        categoryId = "delivery",
                        spentNow = 18_300L,
                        spentPrevious = 10_000L,
                        linkedHabitId = "delivery-habit",
                    ),
                ),
            ),
        )
        val insight = insights.filterIsInstance<Insight.CategoryUpWhileHabitSlipping>().single()
        assertEquals("delivery", insight.categoryId)
        assertEquals(9, insight.cleanDays)
        assertEquals(30, insight.totalDays)
        assertEquals(0.83, insight.growth, 1e-9)
    }

    @Test
    fun `упавший темп цели виден как остановка`() {
        val insights = Insights.of(
            InsightInput(
                goals = listOf(GoalFacts(goalId = "flat", tempoNow = 0.2, tempoPrevious = 0.8)),
            ),
        )
        val insight = insights.filterIsInstance<Insight.GoalStalled>().single()
        assertEquals(0.75, insight.drop, 1e-9)
    }

    @Test
    fun `постоянство есть а глубины нет`() {
        val insights = Insights.of(
            InsightInput(
                habits = listOf(
                    HabitFacts(
                        habitId = "reading",
                        kind = HabitKind.Good,
                        doneDays = 24,
                        plannedDays = 30,
                        normDays = 5,
                        minimumDays = 19,
                    ),
                ),
            ),
        )
        val insight = insights.filterIsInstance<Insight.DepthLow>().single()
        assertEquals(19, insight.minimumDays)
    }

    @Test
    fun `привычка кучкуется в два дня недели хотя план шире`() {
        val insights = Insights.of(
            InsightInput(
                habits = listOf(
                    HabitFacts(
                        habitId = "gym",
                        kind = HabitKind.Good,
                        doneDays = 10,
                        plannedDays = 13,
                        normDays = 10,
                        minimumDays = 0,
                        weekdayHistogram = mapOf(
                            DayOfWeek.TUESDAY to 5,
                            DayOfWeek.FRIDAY to 4,
                            DayOfWeek.MONDAY to 1,
                        ),
                        plannedPerWeek = 3,
                    ),
                ),
            ),
        )
        val insight = insights.filterIsInstance<Insight.HabitClustered>().single()
        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), insight.days)
    }

    @Test
    fun `показывается не больше двух выводов и сначала межблоковые`() {
        val insights = Insights.of(
            InsightInput(
                habits = listOf(
                    HabitFacts("smoking", HabitKind.Bad, doneDays = 5, plannedDays = 30, normDays = 0, minimumDays = 0),
                    HabitFacts("reading", HabitKind.Good, doneDays = 24, plannedDays = 30, normDays = 5, minimumDays = 19),
                ),
                goals = listOf(GoalFacts("flat", tempoNow = 0.2, tempoPrevious = 0.8)),
                categories = listOf(
                    CategoryFacts("tobacco", 20_000L, 10_000L, linkedHabitId = "smoking"),
                ),
            ),
        )
        assertEquals(Insights.MAX_SHOWN, insights.size)
        assertTrue(insights[0] is Insight.CategoryUpWhileHabitSlipping)
        assertTrue(insights[1] is Insight.GoalStalled)
    }

    @Test
    fun `когда всё ровно выводов нет вообще`() {
        val insights = Insights.of(
            InsightInput(
                habits = listOf(
                    HabitFacts("reading", HabitKind.Good, doneDays = 25, plannedDays = 30, normDays = 20, minimumDays = 5),
                ),
                goals = listOf(GoalFacts("flat", tempoNow = 0.75, tempoPrevious = 0.8)),
                categories = listOf(CategoryFacts("food", 10_200L, 10_000L, listOf(10_000L, 9_800L))),
            ),
        )
        assertTrue("похвала и констатация — это шум", insights.isEmpty())
    }
}
