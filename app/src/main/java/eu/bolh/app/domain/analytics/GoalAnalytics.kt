package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.ForecastSource
import eu.bolh.app.domain.model.GoalForecast
import eu.bolh.app.domain.model.GoalSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Счёт целей (§4.2, §4.3, §8.3).
 *
 * У цели два разных числа, и сводить их в одно запрещено: прогресс отвечает
 * «сколько сделано», темп — «двигаюсь ли я вообще». Прогресс 60% при темпе 15%
 * означает, что человек много сделал и остановился, — это отдельный диагноз.
 */
object GoalAnalytics {

    /** Минимум истории, без которого прогноз не показывается вообще (§4.3). */
    const val MIN_HISTORY_DAYS = 21L

    /** Минимум пополнений, ниже которого копилка не даёт права на прогноз. */
    const val MIN_CONTRIBUTIONS = 3

    /** Окно, по которому меряется скорость копилки. */
    const val JAR_WINDOW_DAYS = 90L

    /** Дальше ста лет прогноз смысла не имеет и упирается в границы календаря. */
    private const val MAX_FORECAST_DAYS = 36_500L

    /** прогресс = закрытых этапов / всего этапов. Без этапов показывать нечего. */
    fun progress(stagesTotal: Int, stagesClosed: Int): Double? {
        if (stagesTotal <= 0) return null
        return (stagesClosed.toDouble() / stagesTotal).coerceIn(0.0, 1.0)
    }

    /**
     * темп цели = среднее темпов привязанных привычек.
     * Если привычек нет, темпа нет — и это не ноль: ноль читался бы как «цель стоит».
     */
    fun tempo(habitTempos: List<Double>): Double? {
        if (habitTempos.isEmpty()) return null
        return habitTempos.average()
    }

    /**
     * Прогноз даты по фактическому темпу (§4.3).
     *
     * Требует минимум 21 день истории И хотя бы один закрытый этап или три
     * пополнения копилки. До этого возвращает null: приложение не имеет права
     * выдумывать дату.
     *
     * Если работают оба источника, берётся более поздняя дата — цель достигнута
     * только когда выполнены оба условия.
     */
    fun forecast(snapshot: GoalSnapshot): GoalForecast? {
        val historyDays = ChronoUnit.DAYS.between(snapshot.startDate, snapshot.today)
        if (historyDays < MIN_HISTORY_DAYS) return null

        val contributions = snapshot.contributions.filter { !it.date.isAfter(snapshot.today) }
        val allowed = snapshot.stagesClosed >= 1 || contributions.size >= MIN_CONTRIBUTIONS
        if (!allowed) return null

        val byStages = stageDate(snapshot, historyDays)
        val byJar = jarDate(snapshot, contributions)
        val date = listOfNotNull(byStages, byJar).maxOrNull() ?: return null

        val source = when {
            byStages != null && byJar != null -> ForecastSource.Both
            byStages != null -> ForecastSource.Stages
            else -> ForecastSource.Jar
        }
        val months = snapshot.deadline?.let {
            ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(date))
        }
        return GoalForecast(date = date, source = source, monthsVsDeadline = months)
    }

    /** скорость по этапам = закрытых этапов / дней от старта. */
    private fun stageDate(snapshot: GoalSnapshot, historyDays: Long): LocalDate? {
        if (snapshot.stagesTotal <= 0 || snapshot.stagesClosed <= 0) return null
        val remaining = snapshot.stagesTotal - snapshot.stagesClosed
        if (remaining <= 0) return snapshot.today

        val speed = snapshot.stagesClosed.toDouble() / historyDays
        if (speed <= 0.0) return null
        return snapshot.today.plusDays(daysNeeded(remaining.toDouble() / speed))
    }

    /** скорость копилки = прирост за 90 дней / 90. */
    private fun jarDate(snapshot: GoalSnapshot, contributions: List<Contribution>): LocalDate? {
        val target = snapshot.jarTargetMinor ?: return null
        val current = snapshot.jarCurrentMinor ?: return null
        if (contributions.size < MIN_CONTRIBUTIONS) return null

        val remaining = target - current
        if (remaining <= 0L) return snapshot.today

        val windowStart = snapshot.today.minusDays(JAR_WINDOW_DAYS - 1)
        val grown = contributions
            .filter { !it.date.isBefore(windowStart) }
            .sumOf { it.amountMinor }
        val speed = grown.toDouble() / JAR_WINDOW_DAYS
        if (speed <= 0.0) return null
        return snapshot.today.plusDays(daysNeeded(remaining / speed))
    }

    private fun daysNeeded(raw: Double): Long =
        ceil(raw).toLong().coerceIn(0L, MAX_FORECAST_DAYS)
}
