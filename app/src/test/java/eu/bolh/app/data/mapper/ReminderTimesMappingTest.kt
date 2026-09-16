package eu.bolh.app.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Времена напоминаний в базе: первое в своей колонке, остальные строкой.
 * Копии со старых версий знают только первое, и это должно читаться.
 */
class ReminderTimesMappingTest {

    @Test
    fun `старая запись — одно время`() {
        assertEquals(listOf(540), reminderTimesOf(540, ""))
    }

    @Test
    fun `без напоминаний — пусто`() {
        assertEquals(emptyList<Int>(), reminderTimesOf(null, ""))
    }

    @Test
    fun `несколько времён — по порядку и без повторов`() {
        assertEquals(listOf(540, 780, 1020), reminderTimesOf(1020, "780, 540,1020"))
    }

    @Test
    fun `мусор в строке пропускается`() {
        assertEquals(listOf(600, 720), reminderTimesOf(600, "abc,,720, -5,1440"))
    }

    @Test
    fun `нормализация отбрасывает время за пределами суток`() {
        assertEquals(listOf(0, 1439), normalizeReminderTimes(listOf(1439, -1, 0, 1440, 0)))
    }
}
