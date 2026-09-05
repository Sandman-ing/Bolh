package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.DeadlineRecord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadlineAnalyticsTest {

    private val start = LocalDate.of(2026, 1, 1)

    /** Запись со сроком в 100 дней и фактом, растянутым в [k] раз. */
    private fun record(k: Double) = DeadlineRecord(
        plannedStart = start,
        plannedEnd = start.plusDays(100),
        actualEnd = start.plusDays((100 * k).toLong()),
    )

    @Test
    fun `k это отношение факта к плану`() {
        val overrun = DeadlineRecord(
            plannedStart = LocalDate.of(2025, 1, 1),
            plannedEnd = LocalDate.of(2025, 7, 1),
            actualEnd = LocalDate.of(2025, 10, 1),
        )
        assertEquals(273.0 / 181.0, DeadlineAnalytics.k(overrun), 1e-9)
    }

    @Test
    fun `до пяти завершённых целей коэффициента нет`() {
        val four = listOf(record(1.0), record(1.5), record(2.0), record(2.5))
        assertNull(DeadlineAnalytics.factor(four))
    }

    @Test
    fun `коэффициент это медиана а не среднее`() {
        // Одна цель, растянувшаяся втрое, не должна перекашивать картину.
        val records = listOf(record(1.0), record(1.2), record(1.5), record(2.0), record(3.0))
        assertEquals(1.5, DeadlineAnalytics.factor(records)!!, 1e-9)
    }

    @Test
    fun `при чётном числе целей медиана усредняет середину`() {
        val records = listOf(record(1.0), record(1.2), record(1.5), record(2.0), record(3.0), record(4.0))
        assertEquals((1.5 + 2.0) / 2.0, DeadlineAnalytics.factor(records)!!, 1e-9)
    }

    @Test
    fun `уложился в срок считается по факту не позже плана`() {
        val records = listOf(record(0.8), record(1.0), record(1.4), record(2.0))
        assertEquals(2, DeadlineAnalytics.keptCount(records))
    }

    @Test
    fun `реалистичная дата растягивает план на коэффициент`() {
        val planned = LocalDate.of(2026, 5, 1)
        val realistic = DeadlineAnalytics.realisticDeadline(start, planned, factor = 1.8)
        // 120 плановых дней × 1,8 = 216 дней от старта.
        assertEquals(LocalDate.of(2026, 8, 5), realistic)
    }

    @Test
    fun `перенос считает остаток от реалистичной длительности а не прибавляет столько же`() {
        // План 120 дней, коэффициент 1,8 → реально нужно 216. Прошёл 151 день,
        // значит осталось 65, а не ещё 120.
        val newDeadline = DeadlineAnalytics.rescheduleFor(
            start = start,
            originalDeadline = LocalDate.of(2026, 5, 1),
            today = LocalDate.of(2026, 6, 1),
            factor = 1.8,
        )
        assertEquals(LocalDate.of(2026, 8, 5), newDeadline)
    }

    @Test
    fun `если время вышло даже с поправкой срок всё равно в будущем`() {
        val newDeadline = DeadlineAnalytics.rescheduleFor(
            start = start,
            originalDeadline = LocalDate.of(2026, 2, 1),
            today = LocalDate.of(2026, 6, 1),
            factor = 1.5,
        )
        assertEquals(LocalDate.of(2026, 6, 15), newDeadline)
    }

    @Test
    fun `коэффициент меньше единицы двигает срок ближе`() {
        val planned = LocalDate.of(2026, 5, 1)
        val realistic = DeadlineAnalytics.realisticDeadline(start, planned, factor = 0.5)
        assertEquals(start.plusDays(60), realistic)
    }
}
