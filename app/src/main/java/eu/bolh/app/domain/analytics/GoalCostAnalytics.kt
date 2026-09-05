package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.LinkedHabitCost
import kotlin.math.roundToInt

/**
 * Цена цели в часах и деньгах (§4.5).
 *
 * Это бюджет жизни, а не бюджет денег: у цели есть стоимость во времени,
 * и человек имеет право увидеть её до того, как возьмёт четвёртую цель.
 */
object GoalCostAnalytics {

    /**
     * время в неделю = Σ (частота в неделю × средняя длительность одного выполнения).
     *
     * Привычки без оценки длительности в сумму не входят. Если ни у одной оценки нет,
     * возвращается null — считать не из чего, а нарисовать ноль было бы враньём.
     */
    fun minutesPerWeek(habits: List<LinkedHabitCost>): Int? {
        val known = habits.filter { (it.estimatedMinutes ?: 0) > 0 }
        if (known.isEmpty()) return null
        val minutes = known.sumOf { it.schedule.perWeek * (it.estimatedMinutes ?: 0) }
        return minutes.roundToInt()
    }

    /** Сколько привычек привязано к цели, но не имеет оценки длительности. */
    fun habitsWithoutEstimate(habits: List<LinkedHabitCost>): Int =
        habits.count { (it.estimatedMinutes ?: 0) <= 0 }

    /**
     * деньги в месяц = плановое пополнение копилки + средние траты за 3 месяца
     * по категориям, помеченным как относящиеся к цели.
     */
    fun moneyPerMonthMinor(plannedMonthlyMinor: Long, spendLastThreeMonthsMinor: Long): Long =
        plannedMonthlyMinor + spendLastThreeMonthsMinor / 3L
}
