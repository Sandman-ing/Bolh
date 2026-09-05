package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.ForecastSource
import eu.bolh.app.domain.model.GoalSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalAnalyticsTest {

    private val today = LocalDate.of(2026, 3, 29)
    private val started = today.minusDays(100)

    private fun snapshot(
        startDate: LocalDate = started,
        deadline: LocalDate? = null,
        stagesTotal: Int = 0,
        stagesClosed: Int = 0,
        jarTargetMinor: Long? = null,
        jarCurrentMinor: Long? = null,
        contributions: List<Contribution> = emptyList(),
    ) = GoalSnapshot(
        startDate = startDate,
        today = today,
        deadline = deadline,
        stagesTotal = stagesTotal,
        stagesClosed = stagesClosed,
        jarTargetMinor = jarTargetMinor,
        jarCurrentMinor = jarCurrentMinor,
        contributions = contributions,
    )

    @Test
    fun `прогресс это доля закрытых этапов`() {
        assertEquals(0.6, GoalAnalytics.progress(stagesTotal = 5, stagesClosed = 3)!!, 1e-9)
    }

    @Test
    fun `без этапов прогресса нет`() {
        assertNull(GoalAnalytics.progress(stagesTotal = 0, stagesClosed = 0))
    }

    @Test
    fun `темп цели это среднее темпов привязанных привычек`() {
        assertEquals(0.6, GoalAnalytics.tempo(listOf(0.8, 0.4))!!, 1e-9)
    }

    @Test
    fun `без привязанных привычек темпа нет а не ноль`() {
        assertNull(GoalAnalytics.tempo(emptyList()))
    }

    @Test
    fun `до трёх недель истории прогноза нет`() {
        val young = snapshot(startDate = today.minusDays(20), stagesTotal = 10, stagesClosed = 5)
        assertNull(GoalAnalytics.forecast(young))
    }

    @Test
    fun `без закрытого этапа и без трёх пополнений прогноза нет`() {
        val thin = snapshot(
            stagesTotal = 10,
            stagesClosed = 0,
            jarTargetMinor = 100_000L,
            jarCurrentMinor = 10_000L,
            contributions = listOf(
                Contribution(today.minusDays(60), 5_000L),
                Contribution(today.minusDays(30), 5_000L),
            ),
        )
        assertNull(GoalAnalytics.forecast(thin))
    }

    @Test
    fun `прогноз по этапам считает от фактической скорости`() {
        // 2 этапа за 100 дней, осталось 8 → при этой скорости ещё 400 дней.
        val forecast = GoalAnalytics.forecast(snapshot(stagesTotal = 10, stagesClosed = 2))!!
        assertEquals(today.plusDays(400), forecast.date)
        assertEquals(ForecastSource.Stages, forecast.source)
    }

    @Test
    fun `прогноз по копилке считает от прироста за 90 дней`() {
        // 90 000 за 90 дней = 1000 в день, остаток 60 000 → 60 дней.
        val forecast = GoalAnalytics.forecast(
            snapshot(
                jarTargetMinor = 100_000L,
                jarCurrentMinor = 40_000L,
                contributions = listOf(
                    Contribution(today.minusDays(80), 30_000L),
                    Contribution(today.minusDays(50), 30_000L),
                    Contribution(today.minusDays(20), 30_000L),
                ),
            ),
        )!!
        assertEquals(today.plusDays(60), forecast.date)
        assertEquals(ForecastSource.Jar, forecast.source)
    }

    @Test
    fun `при двух источниках берётся более поздняя дата`() {
        val forecast = GoalAnalytics.forecast(
            snapshot(
                stagesTotal = 10,
                stagesClosed = 2,
                jarTargetMinor = 100_000L,
                jarCurrentMinor = 40_000L,
                contributions = listOf(
                    Contribution(today.minusDays(80), 30_000L),
                    Contribution(today.minusDays(50), 30_000L),
                    Contribution(today.minusDays(20), 30_000L),
                ),
            ),
        )!!
        assertEquals(today.plusDays(400), forecast.date)
        assertEquals(ForecastSource.Both, forecast.source)
    }

    @Test
    fun `отставание от срока считается в месяцах`() {
        val forecast = GoalAnalytics.forecast(
            snapshot(
                deadline = LocalDate.of(2026, 12, 1),
                stagesTotal = 10,
                stagesClosed = 2,
            ),
        )!!
        // Прогноз — май 2027, срок — декабрь 2026: пять месяцев позже.
        assertEquals(5L, forecast.monthsVsDeadline)
    }

    @Test
    fun `опережение срока показывается отрицательным числом`() {
        val forecast = GoalAnalytics.forecast(
            snapshot(
                deadline = LocalDate.of(2028, 1, 1),
                stagesTotal = 10,
                stagesClosed = 2,
            ),
        )!!
        assertEquals(-8L, forecast.monthsVsDeadline)
    }

    @Test
    fun `все этапы закрыты — прогноз на сегодня`() {
        val forecast = GoalAnalytics.forecast(snapshot(stagesTotal = 4, stagesClosed = 4))!!
        assertEquals(today, forecast.date)
    }
}
