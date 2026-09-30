package eu.bolh.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Периодичность платежа в строке RRULE (§6.5).
 *
 * Разовый платёж хранится в той же строке как COUNT=1: схема базы не
 * меняется, а платежи, заведённые до разовых, должны читаться как прежде.
 */
class RecurrenceTest {

    @Test
    fun `прежние строки читаются как регулярный платёж`() {
        val recurrence = Recurrence.parse("FREQ=MONTHLY;INTERVAL=1")
        assertEquals(Recurrence(RecurrenceUnit.Month, 1), recurrence)
        assertFalse(recurrence.once)
    }

    @Test
    fun `регулярный платёж пишется без COUNT`() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2", Recurrence(RecurrenceUnit.Week, 2).toRRule())
    }

    @Test
    fun `разовый платёж пишется как COUNT=1`() {
        assertEquals(
            "FREQ=MONTHLY;INTERVAL=1;COUNT=1",
            Recurrence(RecurrenceUnit.Month, 1, once = true).toRRule(),
        )
    }

    @Test
    fun `разовый платёж переживает сохранение и чтение`() {
        val once = Recurrence(RecurrenceUnit.Week, 3, once = true)
        assertEquals(once, Recurrence.parse(once.toRRule()))
    }

    @Test
    fun `COUNT больше единицы разовым не считается`() {
        assertFalse(Recurrence.parse("FREQ=DAILY;INTERVAL=1;COUNT=5").once)
        assertTrue(Recurrence.parse("freq=daily;count=1").once)
    }
}
