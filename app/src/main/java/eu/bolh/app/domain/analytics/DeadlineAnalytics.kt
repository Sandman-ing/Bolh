package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.DeadlineRecord
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Личный коэффициент сроков (§4.4).
 *
 * Приложение запоминает каждый срок, который человек себе поставил, и сравнивает
 * с фактом. Коэффициент — медиана, а не среднее: одна цель, растянувшаяся впятеро,
 * не должна перекашивать всю картину.
 *
 * Считается от ПЕРВОГО поставленного срока. Если считать от последнего переноса,
 * коэффициент всегда стремился бы к единице — последний срок обычно уже реалистичен,
 * и механика тихо самоуничтожилась бы.
 */
object DeadlineAnalytics {

    /** До пяти завершённых целей механика молчит. */
    const val MIN_COMPLETED_GOALS = 5

    /** k = фактическая длительность / запланированная длительность. */
    fun k(record: DeadlineRecord): Double {
        val planned = max(1L, ChronoUnit.DAYS.between(record.plannedStart, record.plannedEnd))
        val actual = max(0L, ChronoUnit.DAYS.between(record.plannedStart, record.actualEnd))
        return actual.toDouble() / planned
    }

    /** Коэффициент = медиана всех k. Null, пока завершённых целей меньше пяти. */
    fun factor(records: List<DeadlineRecord>): Double? {
        if (records.size < MIN_COMPLETED_GOALS) return null
        return median(records.map { k(it) })
    }

    /** Сколько раз человек уложился в срок. Нужно для текста «из 9 сроков ты уложился в 2». */
    fun keptCount(records: List<DeadlineRecord>): Int =
        records.count { !it.actualEnd.isAfter(it.plannedEnd) }

    /**
     * Реалистичная дата для нового срока: плановая длительность, умноженная на коэффициент.
     * Предлагается как равноправная кнопка — человек имеет право быть оптимистом,
     * и его выбор тоже станет данными (§4.4).
     */
    fun realisticDeadline(start: LocalDate, planned: LocalDate, factor: Double): LocalDate {
        val plannedDays = max(1L, ChronoUnit.DAYS.between(start, planned))
        val corrected = (plannedDays * factor).roundToLong().coerceAtLeast(1L)
        return start.plusDays(corrected)
    }

    /**
     * Новый срок для просроченной цели (§4.6).
     *
     * Считается не «прибавить столько же», а «сколько на такую цель уходит у тебя
     * на самом деле, минус уже прошедшее». Иначе человек передвинет дедлайн ровно
     * на столько, на сколько ошибся, и цикл повторится.
     *
     * Если по этому счёту времени уже не осталось, берётся [minimumDays]:
     * срок в прошлом — это не срок.
     */
    fun rescheduleFor(
        start: LocalDate,
        originalDeadline: LocalDate,
        today: LocalDate,
        factor: Double,
        minimumDays: Long = 14L,
    ): LocalDate {
        val plannedDays = max(1L, ChronoUnit.DAYS.between(start, originalDeadline))
        val realisticTotal = (plannedDays * factor).roundToLong()
        val elapsed = max(0L, ChronoUnit.DAYS.between(start, today))
        val remaining = realisticTotal - elapsed
        return today.plusDays(max(minimumDays, remaining))
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
