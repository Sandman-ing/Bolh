package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.EntryLevel
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.HabitSchedule
import eu.bolh.app.domain.model.Mark
import eu.bolh.app.domain.model.Pause
import eu.bolh.app.domain.model.ScheduleType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Счёт привычек (§8.3). Не знает ни про Android, ни про базу: только даты и числа,
 * чтобы этот код переехал на iOS без изменений и проверялся юнит-тестами.
 *
 * Общее правило: нет знаменателя — возвращается null, а не ноль.
 * Ноль означал бы «человек не делал», а это другое утверждение.
 */
object HabitAnalytics {

    /**
     * Дни, закрытые полностью.
     *
     * Привычку могут ждать несколько раз за день: зубы утром и на ночь. Одна
     * отметка из двух — это половина дня, и в счёт она идти не должна, иначе
     * темп и серия начнут показывать больше, чем было на самом деле (§5.5).
     *
     * Срывы вредных привычек не сворачиваются: их за день бывает несколько,
     * и каждый — отдельное событие.
     */
    fun completeDays(marks: List<Mark>, timesPerDay: Int): List<Mark> {
        if (timesPerDay <= 1) return marks
        val slips = marks.filter { it.level == EntryLevel.Slip }
        val done = marks.filterNot { it.level == EntryLevel.Slip }
            .groupBy { it.date }
            .filterValues { it.size >= timesPerDay }
            .values
            .mapNotNull { onDay -> onDay.maxByOrNull { levelWeight(it.level) } }
        return (done + slips).sortedBy { it.date }
    }

    /** Норма весит больше минимума: у дня остаётся лучший достигнутый уровень. */
    private fun levelWeight(level: EntryLevel): Int = when (level) {
        EntryLevel.Norm -> 2
        EntryLevel.Minimum -> 1
        EntryLevel.Slip -> 0
    }

    /** Дни окна, когда привычка вообще действовала: после старта и вне пауз. */
    fun activeDays(
        window: DayWindow,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): List<LocalDate> = window.dates().filter { date ->
        !date.isBefore(startDate) && pauses.none { it.covers(date) }
    }

    /** Сколько дней было запланировано. Для «N раз в неделю» — доля недели в окне. */
    fun plannedDays(schedule: HabitSchedule, activeDays: List<LocalDate>): Double =
        when (schedule.type) {
            ScheduleType.Daily -> activeDays.size.toDouble()
            ScheduleType.Weekdays -> activeDays.count { it.dayOfWeek in schedule.days }.toDouble()
            ScheduleType.TimesPerWeek -> (schedule.timesPerWeek ?: 0) * activeDays.size / 7.0
        }

    /**
     * темп = выполненных дней / запланированных дней.
     * Перевыполнение не даёт больше единицы: это доля, а не очки.
     */
    fun tempo(
        marks: List<Mark>,
        schedule: HabitSchedule,
        window: DayWindow,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): Double? {
        val active = activeDays(window, startDate, pauses)
        val planned = plannedDays(schedule, active)
        if (planned <= 0.0) return null
        val activeSet = active.toHashSet()
        val done = doneDates(marks).count { it in activeSet }
        return (done / planned).coerceAtMost(1.0)
    }

    /** Сколько дней окна отмечено хоть чем-то. Для строки «18 из 30 дней». */
    fun doneDays(
        marks: List<Mark>,
        window: DayWindow,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): Int {
        val active = activeDays(window, startDate, pauses).toHashSet()
        return doneDates(marks).count { it in active }
    }

    /** Сколько дней окна было запланировано, округлённо. Вторая половина той же строки. */
    fun plannedDaysRounded(
        schedule: HabitSchedule,
        window: DayWindow,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): Int = plannedDays(schedule, activeDays(window, startDate, pauses)).roundToInt()

    /** Постоянство — доля дней, когда сделано хоть что-то. Строит привычку (§5.3). */
    fun consistency(
        marks: List<Mark>,
        schedule: HabitSchedule,
        window: DayWindow,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): Double? = tempo(marks, schedule, window, startDate, pauses)

    /**
     * глубина = дней с нормой / дней с любым выполнением (§5.3).
     * Постоянство строит привычку, глубина даёт результат — это два разных числа,
     * и сводить их в одно нельзя.
     */
    fun depth(
        marks: List<Mark>,
        window: DayWindow,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): Double? {
        val active = activeDays(window, startDate, pauses).toHashSet()
        val byDate = bestLevelByDate(marks).filterKeys { it in active }
        if (byDate.isEmpty()) return null
        val norm = byDate.values.count { it == EntryLevel.Norm }
        return norm.toDouble() / byDate.size
    }

    /**
     * Серия. Возвращает null там, где серия не метрика: в режиме минимума (§5.2)
     * и у расписания «N раз в неделю», где нет конкретных дней.
     *
     * Сегодняшний неотмеченный день серию не рвёт: день ещё не кончился.
     */
    fun streak(
        mode: HabitMode,
        marks: List<Mark>,
        schedule: HabitSchedule,
        today: LocalDate,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
        skipAllowancePerMonth: Int = 0,
    ): Int? {
        if (mode == HabitMode.Minimum) return null
        if (schedule.type == ScheduleType.TimesPerWeek) return null

        val done = doneDates(marks)
        val skipsUsed = HashMap<YearMonth, Int>()
        var streak = 0
        var date = today

        val todayOpen = schedule.plannedOn(date) &&
            date !in done &&
            pauses.none { it.covers(date) }
        if (todayOpen) date = date.minusDays(1)

        while (!date.isBefore(startDate)) {
            val paused = pauses.any { it.covers(date) }
            when {
                paused -> Unit
                !schedule.plannedOn(date) -> Unit
                date in done -> streak++
                mode == HabitMode.Skips -> {
                    val month = YearMonth.from(date)
                    val used = skipsUsed[month] ?: 0
                    if (used >= skipAllowancePerMonth) return streak
                    skipsUsed[month] = used + 1
                }
                else -> return streak
            }
            date = date.minusDays(1)
        }
        return streak
    }

    /**
     * Два пропуска подряд — планка опускается до минимума на неделю (§5.3).
     * Смысл в том, чтобы человек вернулся, а не бросил.
     */
    fun needsRecovery(
        marks: List<Mark>,
        schedule: HabitSchedule,
        today: LocalDate,
        startDate: LocalDate,
        pauses: List<Pause> = emptyList(),
    ): Boolean {
        val done = doneDates(marks)
        var misses = 0
        var date = today.minusDays(1)
        val floorDate = maxOf(startDate, today.minusDays(8))

        while (!date.isBefore(floorDate)) {
            val counts = pauses.none { it.covers(date) } && schedule.plannedOn(date)
            if (counts) {
                if (date in done) {
                    misses = 0
                } else {
                    misses++
                    if (misses >= 2) return true
                }
            }
            date = date.minusDays(1)
        }
        return false
    }

    // --- Вредные привычки -------------------------------------------------

    /** «9 дней без срыва» (§5.4). Считается от последнего срыва либо от старта. */
    fun daysWithoutSlip(slips: List<LocalDate>, today: LocalDate, startDate: LocalDate): Int {
        val last = slips.filter { !it.isAfter(today) }.maxOrNull()
            ?: return daysBetween(startDate, today)
        return daysBetween(last, today)
    }

    /** «2 из 3 за неделю» (§5.4). Неделя задаётся своим первым днём. */
    fun slipsInWeek(slips: List<LocalDate>, weekStart: LocalDate): Int {
        val window = DayWindow(weekStart, weekStart.plusDays(6))
        return slips.count { window.covers(it) }
    }

    /**
     * Накопленная экономия (§5.4). Частота берётся из собственной истории привычки
     * до начала текущего воздержания: приложение не имеет права предполагать,
     * что человек срывался ровно раз в день.
     *
     * Если истории меньше [minHistoryDays] дней — берётся один случай в день,
     * и это единственное допущение в расчёте.
     */
    fun avoidedCostMinor(
        slips: List<LocalDate>,
        today: LocalDate,
        startDate: LocalDate,
        costPerOccurrenceMinor: Long,
        minHistoryDays: Int = 14,
    ): Long {
        val cleanDays = daysWithoutSlip(slips, today, startDate)
        if (cleanDays <= 0 || costPerOccurrenceMinor <= 0L) return 0L

        val lastSlip = slips.filter { !it.isAfter(today) }.maxOrNull()
        val rate = if (lastSlip == null) {
            1.0
        } else {
            val historyDays = daysBetween(startDate, lastSlip)
            val before = slips.count { !it.isAfter(lastSlip) }
            if (historyDays >= minHistoryDays && before > 0) {
                before.toDouble() / historyDays
            } else {
                1.0
            }
        }
        return floor(rate * cleanDays * costPerOccurrenceMinor).toLong()
    }

    // --- Внутреннее -------------------------------------------------------

    /** Даты, где сделано хоть что-то. Срыв выполнением не считается. */
    private fun doneDates(marks: List<Mark>): Set<LocalDate> =
        marks.asSequence()
            .filter { it.level != EntryLevel.Slip }
            .map { it.date }
            .toHashSet()

    /** Если за день пришло несколько отметок, норма перевешивает минимум. */
    private fun bestLevelByDate(marks: List<Mark>): Map<LocalDate, EntryLevel> {
        val out = HashMap<LocalDate, EntryLevel>()
        for (mark in marks) {
            if (mark.level == EntryLevel.Slip) continue
            val current = out[mark.date]
            if (current == null || (current == EntryLevel.Minimum && mark.level == EntryLevel.Norm)) {
                out[mark.date] = mark.level
            }
        }
        return out
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).toInt()
}
