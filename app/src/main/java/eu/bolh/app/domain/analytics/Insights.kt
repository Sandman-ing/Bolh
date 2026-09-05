package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.HabitKind
import java.time.DayOfWeek

/**
 * Выводы (§7.1). В первой версии ИИ нет: правила локальные, показывается максимум два.
 *
 * Главное правило: вывод имеет право на существование, только если человек
 * не увидел бы этого сам, глядя на график. Поздравления и констатации вида
 * «ты выполнил 5 привычек» — шум, после которого статистику перестают открывать.
 *
 * Когда появится ИИ-слой, он встанет ровно на это место: механика останется,
 * вырастет качество выводов.
 */
sealed interface Insight {

    /** Насколько вывод важен. Межблоковые выводы идут первыми: их не даст ни одно
     *  отдельное приложение для привычек или для денег. */
    val weight: Int

    /** Категория выросла, а связанная с ней вредная привычка не держалась. */
    data class CategoryUpWhileHabitSlipping(
        val categoryId: String,
        val growth: Double,
        val habitId: String,
        val cleanDays: Int,
        val totalDays: Int,
    ) : Insight {
        override val weight = 100
    }

    /** Темп цели упал: цель стоит, хотя формально не провалена. */
    data class GoalStalled(
        val goalId: String,
        val tempoNow: Double,
        val drop: Double,
    ) : Insight {
        override val weight = 90
    }

    /** Пополнение копилки замедлилось — прогнозная дата уехала. */
    data class JarSlowed(
        val goalId: String,
        val monthsShift: Long,
    ) : Insight {
        override val weight = 80
    }

    /** Постоянство есть, глубины нет: привычка держится, но на минимуме. */
    data class DepthLow(
        val habitId: String,
        val doneDays: Int,
        val plannedDays: Int,
        val minimumDays: Int,
    ) : Insight {
        override val weight = 60
    }

    /** Привычка случается только в определённые дни недели, хотя план шире. */
    data class HabitClustered(
        val habitId: String,
        val days: List<DayOfWeek>,
        val plannedPerWeek: Int,
    ) : Insight {
        override val weight = 50
    }

    /** Расход аномален относительно собственной истории категории. */
    data class CategoryAnomaly(
        val categoryId: String,
        val growth: Double,
    ) : Insight {
        override val weight = 40
    }
}

data class HabitFacts(
    val habitId: String,
    val kind: HabitKind,
    val doneDays: Int,
    val plannedDays: Int,
    val normDays: Int,
    val minimumDays: Int,
    val weekdayHistogram: Map<DayOfWeek, Int> = emptyMap(),
    val plannedPerWeek: Int = 0,
)

data class GoalFacts(
    val goalId: String,
    val tempoNow: Double?,
    val tempoPrevious: Double?,
    val jarMonthsShift: Long? = null,
)

data class CategoryFacts(
    val categoryId: String,
    val spentNow: Long,
    val spentPrevious: Long,
    val historyMonthly: List<Long> = emptyList(),
    val linkedHabitId: String? = null,
)

data class InsightInput(
    val habits: List<HabitFacts> = emptyList(),
    val goals: List<GoalFacts> = emptyList(),
    val categories: List<CategoryFacts> = emptyList(),
)

object Insights {

    const val MAX_SHOWN = 2

    private const val CATEGORY_GROWTH = 0.5
    private const val HABIT_HELD_SHARE = 0.5
    private const val TEMPO_DROP = 0.3
    private const val CONSISTENCY_HIGH = 0.7
    private const val DEPTH_LOW = 0.4
    private const val CLUSTER_SHARE = 0.8

    fun of(input: InsightInput): List<Insight> {
        val habitsById = input.habits.associateBy { it.habitId }
        val found = buildList {
            addAll(crossBlock(input, habitsById))
            addAll(withinBlock(input))
        }
        return found.sortedByDescending { it.weight }.take(MAX_SHOWN)
    }

    private fun crossBlock(
        input: InsightInput,
        habitsById: Map<String, HabitFacts>,
    ): List<Insight> = buildList {
        input.categories.forEach { category ->
            val growth = FinanceAnalytics.delta(category.spentNow, category.spentPrevious)
            val habit = category.linkedHabitId?.let { habitsById[it] }
            if (growth != null && growth > CATEGORY_GROWTH && habit != null && habit.plannedDays > 0) {
                val held = habit.doneDays.toDouble() / habit.plannedDays
                if (held < HABIT_HELD_SHARE) {
                    add(
                        Insight.CategoryUpWhileHabitSlipping(
                            categoryId = category.categoryId,
                            growth = growth,
                            habitId = habit.habitId,
                            cleanDays = habit.doneDays,
                            totalDays = habit.plannedDays,
                        ),
                    )
                }
            }
        }

        input.goals.forEach { goal ->
            val now = goal.tempoNow
            val previous = goal.tempoPrevious
            if (now != null && previous != null && previous > 0.0) {
                val drop = (previous - now) / previous
                if (drop > TEMPO_DROP) {
                    add(Insight.GoalStalled(goal.goalId, now, drop))
                }
            }
            goal.jarMonthsShift?.let { shift ->
                if (shift > 0L) add(Insight.JarSlowed(goal.goalId, shift))
            }
        }
    }

    private fun withinBlock(input: InsightInput): List<Insight> = buildList {
        input.habits.forEach { habit ->
            if (habit.kind == HabitKind.Good && habit.plannedDays > 0 && habit.doneDays > 0) {
                val consistency = habit.doneDays.toDouble() / habit.plannedDays
                val depth = habit.normDays.toDouble() / habit.doneDays
                if (consistency > CONSISTENCY_HIGH && depth < DEPTH_LOW) {
                    add(
                        Insight.DepthLow(
                            habitId = habit.habitId,
                            doneDays = habit.doneDays,
                            plannedDays = habit.plannedDays,
                            minimumDays = habit.minimumDays,
                        ),
                    )
                }
            }

            // «Зал случается только по вторникам и пятницам, хотя запланирован на три раза»
            val total = habit.weekdayHistogram.values.sum()
            if (habit.plannedPerWeek >= 3 && total >= 4) {
                val top = habit.weekdayHistogram.entries
                    .sortedByDescending { it.value }
                    .take(2)
                val share = top.sumOf { it.value }.toDouble() / total
                if (share >= CLUSTER_SHARE) {
                    add(
                        Insight.HabitClustered(
                            habitId = habit.habitId,
                            days = top.map { it.key }.sortedBy { it.value },
                            plannedPerWeek = habit.plannedPerWeek,
                        ),
                    )
                }
            }
        }

        input.categories.forEach { category ->
            if (FinanceAnalytics.isAnomalous(category.spentNow, category.historyMonthly)) {
                val growth = FinanceAnalytics.delta(category.spentNow, category.spentPrevious)
                if (growth != null) add(Insight.CategoryAnomaly(category.categoryId, growth))
            }
        }
    }
}
