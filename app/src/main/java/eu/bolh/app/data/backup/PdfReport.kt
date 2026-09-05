package eu.bolh.app.data.backup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import eu.bolh.app.R
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.data.repository.HabitRepository
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.analytics.GoalAnalytics
import eu.bolh.app.domain.analytics.HabitAnalytics
import eu.bolh.app.domain.model.DayWindow
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.templates.SystemCategories
import java.io.OutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Срез на бумагу.
 *
 * Отчёт печатает те же числа, что и экраны, и ровно так же молчит там, где
 * данных нет: «—» вместо нуля, если знаменателя не существует.
 */
class PdfReport(
    private val context: Context,
    private val habits: HabitRepository,
    private val goals: GoalRepository,
    private val finance: FinanceRepository,
) {

    suspend fun write(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val lines = buildLines()
        val document = PdfDocument()
        val title = Paint().apply {
            color = INK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = INK
            textSize = 11f
            isAntiAlias = true
        }
        val muted = Paint().apply {
            color = GREY
            textSize = 11f
            isAntiAlias = true
        }

        var pageNumber = 1
        var page = document.startPage(pageInfo(pageNumber))
        var canvas: Canvas = page.canvas
        var y = MARGIN + 24f

        canvas.drawText(context.getString(R.string.app_name), MARGIN, y, title)
        y += 10f
        canvas.drawText(
            java.time.LocalDate.now().format(DateTimeFormatter.ISO_DATE),
            MARGIN,
            y + 12f,
            muted,
        )
        y += 40f

        lines.forEach { line ->
            if (y > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(pageInfo(pageNumber))
                canvas = page.canvas
                y = MARGIN + 20f
            }
            when (line) {
                is Line.Header -> {
                    y += 12f
                    canvas.drawText(line.text, MARGIN, y, title.apply { textSize = 13f })
                    y += 16f
                }

                is Line.Body -> {
                    canvas.drawText(line.text, MARGIN, y, body)
                    y += 15f
                }

                is Line.Muted -> {
                    canvas.drawText(line.text, MARGIN, y, muted)
                    y += 15f
                }
            }
        }

        document.finishPage(page)
        document.writeTo(output)
        document.close()
        lines.size
    }

    private suspend fun buildLines(): List<Line> {
        val today = habits.today()
        val window = DayWindow.lastDays(today, 30)
        val month = YearMonth.from(today)

        val habitList = habits.observeAllHabits().first()
        val marks = habits.observeMarks(today.minusDays(120), today).first()
        val goalList = goals.observeActiveGoals().first()
        val stages = goals.observeStages().first()
        val accounts = finance.observeAccounts().first()
        val transactions = finance.observeTransactions().first()
        val categories = finance.observeCategories().first().associateBy { it.id }

        return buildList {
            add(Line.Header(context.getString(R.string.stats_block_habits)))
            val good = habitList.filter { it.kind == HabitKind.Good }
            if (good.isEmpty()) {
                add(Line.Muted(context.getString(R.string.stats_no_habits)))
            } else {
                good.forEach { habit ->
                    val done = HabitAnalytics.doneDays(
                        marks[habit.id].orEmpty(),
                        window,
                        habit.startDate,
                    )
                    val planned = HabitAnalytics.plannedDaysRounded(
                        habit.schedule,
                        window,
                        habit.startDate,
                    )
                    add(Line.Body("${habit.name}: $done / $planned"))
                }
            }

            add(Line.Header(context.getString(R.string.stats_block_goals)))
            if (goalList.isEmpty()) {
                add(Line.Muted(context.getString(R.string.stats_no_goals)))
            } else {
                goalList.forEach { goal ->
                    val goalStages = stages[goal.id].orEmpty()
                    val progress = GoalAnalytics.progress(
                        goalStages.size,
                        goalStages.count { it.isDone },
                    )
                    val percent = progress?.let { "${(it * 100).roundToInt()}%" } ?: "—"
                    add(Line.Body("${goal.name}: $percent"))
                }
            }

            add(Line.Header(context.getString(R.string.stats_block_finance)))
            if (accounts.isEmpty()) {
                add(Line.Muted(context.getString(R.string.finance_empty_action)))
            } else {
                accounts.forEach { account ->
                    val balance = FinanceAnalytics.balanceOf(account, transactions)
                    add(Line.Body("${account.name}: ${money(balance, account.currencyCode)}"))
                }
                val spend = FinanceAnalytics.spendByCategory(
                    transactions,
                    month.atDay(1),
                    month.atEndOfMonth(),
                )
                if (spend.isEmpty()) {
                    add(Line.Muted(context.getString(R.string.stats_no_spend)))
                } else {
                    spend.entries.sortedByDescending { it.value }.forEach { (id, amount) ->
                        val category = id?.let { categories[it] }
                        val name = category?.nameKey
                            ?.let { key -> SystemCategories.nameResFor(key)?.let(context::getString) }
                            ?: category?.name
                            ?: context.getString(R.string.finance_uncategorised)
                        add(Line.Body("$name: ${money(amount, finance.baseCurrency)}"))
                    }
                }
            }
        }
    }

    private fun money(minor: Long, currency: String): String =
        eu.bolh.app.ui.common.Money.format(minor, currency)

    private fun pageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create()

    private sealed interface Line {
        data class Header(val text: String) : Line
        data class Body(val text: String) : Line
        data class Muted(val text: String) : Line
    }

    private companion object {
        /** A4 в точках при 72 dpi. */
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 40f
        const val INK = 0xFF1A1A1A.toInt()
        const val GREY = 0xFF8A8A8A.toInt()
    }
}
