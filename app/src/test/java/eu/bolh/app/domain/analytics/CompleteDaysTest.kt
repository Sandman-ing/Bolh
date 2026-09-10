package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.Mark
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * День, который ждут несколько раз, закрывается только целиком (§5.5).
 *
 * Эти проверки стоят отдельно, потому что от них зависит, врёт статистика
 * или нет: половина дня, засчитанная за целый, поднимает и темп, и серию.
 */
class CompleteDaysTest {

    private val day = LocalDate.of(2026, 9, 10)

    private fun norm(date: LocalDate) = Mark(date, EntryLevel.Norm)

    @Test
    fun `один раз в день — список не меняется`() {
        val marks = listOf(norm(day), norm(day.plusDays(1)))
        assertEquals(marks, HabitAnalytics.completeDays(marks, timesPerDay = 1))
    }

    @Test
    fun `одна отметка из двух — день не закрыт`() {
        val marks = listOf(norm(day))
        assertEquals(emptyList<Mark>(), HabitAnalytics.completeDays(marks, timesPerDay = 2))
    }

    @Test
    fun `две отметки из двух — день закрыт один раз`() {
        val marks = listOf(norm(day), norm(day))
        val result = HabitAnalytics.completeDays(marks, timesPerDay = 2)
        assertEquals(1, result.size)
        assertEquals(day, result.first().date)
    }

    @Test
    fun `у дня остаётся лучший достигнутый уровень`() {
        val marks = listOf(Mark(day, EntryLevel.Minimum), norm(day))
        val result = HabitAnalytics.completeDays(marks, timesPerDay = 2)
        assertEquals(EntryLevel.Norm, result.single().level)
    }

    @Test
    fun `закрытые и незакрытые дни разделяются`() {
        val marks = listOf(
            norm(day),
            norm(day),
            norm(day.plusDays(1)),
        )
        val result = HabitAnalytics.completeDays(marks, timesPerDay = 2)
        assertEquals(listOf(day), result.map { it.date })
    }

    @Test
    fun `срывы не сворачиваются — их за день бывает несколько`() {
        val marks = listOf(
            Mark(day, EntryLevel.Slip),
            Mark(day, EntryLevel.Slip),
        )
        val result = HabitAnalytics.completeDays(marks, timesPerDay = 2)
        assertEquals(2, result.size)
    }
}
