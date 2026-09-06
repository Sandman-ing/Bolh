package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.JarMode
import eu.bolh.app.domain.model.SavingsJar
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import java.time.LocalDate

/**
 * Счёт денег. Как и весь домен, ничего не знает про Android и базу.
 *
 * Все суммы — целые минорные единицы. Курс операции берётся тот, что зафиксирован
 * на её дату, и больше не меняется: если пересчитывать историю по сегодняшнему
 * курсу, прошлый месяц начнёт дорожать и дешеветь сам по себе (§6.3).
 */
object FinanceAnalytics {

    /** Баланс счёта: начальный остаток плюс всё, что на него пришло и ушло. */
    fun balanceOf(account: Account, transactions: List<Transaction>): Long {
        var balance = account.initialBalanceMinor
        for (transaction in transactions) {
            when (transaction.type) {
                TransactionType.Income ->
                    if (transaction.accountId == account.id) balance += transaction.amountMinor

                TransactionType.Expense ->
                    if (transaction.accountId == account.id) balance -= transaction.amountMinor

                TransactionType.Transfer -> {
                    if (transaction.accountId == account.id) balance -= transaction.amountMinor
                    if (transaction.toAccountId == account.id) balance += transaction.amountMinor
                }
            }
        }
        return balance
    }

    fun totalBalance(accounts: List<Account>, transactions: List<Transaction>): Long =
        accounts.sumOf { balanceOf(it, transactions) }

    /**
     * Сколько денег обещано целям. Строка обязательна на экране финансов:
     * иначе человек считает своими деньги, уже зарезервированные копилкой (§6.8).
     *
     * «Просто цифра» в резерв не идёт — она не связана с балансом по определению.
     */
    fun reservedInJars(jars: List<SavingsJar>, contributions: Map<String, List<Contribution>>): Long =
        jars.filter { it.mode == JarMode.Envelope }
            .sumOf { jar -> contributions[jar.id].orEmpty().sumOf { it.amountMinor } }

    fun jarBalance(jarId: String, contributions: Map<String, List<Contribution>>): Long =
        contributions[jarId].orEmpty().sumOf { it.amountMinor }

    /** Траты по категориям за период. Переводы расходом не считаются. */
    fun spendByCategory(
        transactions: List<Transaction>,
        from: LocalDate,
        to: LocalDate,
    ): Map<String?, Long> = byCategory(transactions, from, to, TransactionType.Expense)

    /**
     * Суммы по категориям за период для одного вида операций.
     *
     * Доходы разбираются тем же способом, что и траты: вопрос «откуда пришло»
     * ничем не отличается от «куда ушло», и считать его отдельно незачем.
     */
    fun byCategory(
        transactions: List<Transaction>,
        from: LocalDate,
        to: LocalDate,
        type: TransactionType,
    ): Map<String?, Long> = transactions
        .asSequence()
        .filter { it.type == type }
        .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        .groupBy { it.categoryId }
        .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }

    fun totalSpend(transactions: List<Transaction>, from: LocalDate, to: LocalDate): Long =
        transactions
            .filter { it.type == TransactionType.Expense }
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sumOf { it.amountMinor }

    fun totalIncome(transactions: List<Transaction>, from: LocalDate, to: LocalDate): Long =
        transactions
            .filter { it.type == TransactionType.Income }
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sumOf { it.amountMinor }

    /**
     * Категория, выросшая аномально относительно своей же истории (§6.8).
     * Сравнение идёт с самой собой, а не с другими категориями: «на еду больше,
     * чем на транспорт» — это не новость, а «на еду вдвое больше обычного» — новость.
     *
     * Порог намеренно высокий: цвет должен что-то значить, иначе он перестанет
     * читаться уже на втором месяце.
     */
    fun isAnomalous(
        currentMinor: Long,
        historyMonthly: List<Long>,
        threshold: Double = 1.5,
        minimumHistoryMonths: Int = 2,
    ): Boolean {
        val history = historyMonthly.filter { it > 0L }
        if (history.size < minimumHistoryMonths) return false
        val average = history.sum().toDouble() / history.size
        if (average <= 0.0) return false
        return currentMinor > average * threshold
    }

    /** Изменение к прошлому периоду в долях: +0.83 значит «выросло на 83%». */
    fun delta(currentMinor: Long, previousMinor: Long): Double? {
        if (previousMinor <= 0L) return null
        return (currentMinor - previousMinor).toDouble() / previousMinor
    }
}
