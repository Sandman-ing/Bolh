package eu.bolh.app.domain.analytics

import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.JarMode
import eu.bolh.app.domain.model.SavingsJar
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceAnalyticsTest {

    private val card = Account(
        id = "card",
        name = "Карта",
        icon = "credit_card",
        currencyCode = "EUR",
        initialBalanceMinor = 100_000L,
    )
    private val cash = card.copy(id = "cash", name = "Наличные", initialBalanceMinor = 5_000L)
    private val date = LocalDate.of(2026, 3, 10)

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        account: String = "card",
        to: String? = null,
        category: String? = null,
        on: LocalDate = date,
    ) = Transaction(
        id = id,
        accountId = account,
        toAccountId = to,
        type = type,
        amountMinor = amount,
        currencyCode = "EUR",
        date = on,
        categoryId = category,
    )

    @Test
    fun `баланс это начальный остаток плюс приход минус расход`() {
        val transactions = listOf(
            tx("1", TransactionType.Income, 250_000L),
            tx("2", TransactionType.Expense, 30_000L),
            tx("3", TransactionType.Expense, 12_500L),
        )
        assertEquals(100_000L + 250_000L - 42_500L, FinanceAnalytics.balanceOf(card, transactions))
    }

    @Test
    fun `перевод уходит с одного счёта и приходит на другой`() {
        val transactions = listOf(
            tx("1", TransactionType.Transfer, 20_000L, account = "card", to = "cash"),
        )
        assertEquals(80_000L, FinanceAnalytics.balanceOf(card, transactions))
        assertEquals(25_000L, FinanceAnalytics.balanceOf(cash, transactions))
        assertEquals(
            105_000L,
            FinanceAnalytics.totalBalance(listOf(card, cash), transactions),
        )
    }

    @Test
    fun `в резерв идут только конверты а просто цифра нет`() {
        val jars = listOf(
            SavingsJar("envelope", "goal-1", JarMode.Envelope, targetMinor = 500_000L, currencyCode = "EUR"),
            SavingsJar("number", "goal-2", JarMode.Number, targetMinor = 500_000L, currencyCode = "EUR"),
        )
        val contributions = mapOf(
            "envelope" to listOf(Contribution(date, 30_000L), Contribution(date, 20_000L)),
            "number" to listOf(Contribution(date, 90_000L)),
        )
        assertEquals(50_000L, FinanceAnalytics.reservedInJars(jars, contributions))
    }

    @Test
    fun `траты складываются по категориям а переводы не считаются расходом`() {
        val transactions = listOf(
            tx("1", TransactionType.Expense, 4_000L, category = "food"),
            tx("2", TransactionType.Expense, 6_000L, category = "food"),
            tx("3", TransactionType.Expense, 15_000L, category = "home"),
            tx("4", TransactionType.Transfer, 50_000L, to = "cash"),
            tx("5", TransactionType.Income, 100_000L, category = "salary"),
        )
        val byCategory = FinanceAnalytics.spendByCategory(
            transactions,
            from = LocalDate.of(2026, 3, 1),
            to = LocalDate.of(2026, 3, 31),
        )
        assertEquals(10_000L, byCategory["food"])
        assertEquals(15_000L, byCategory["home"])
        assertEquals(25_000L, FinanceAnalytics.totalSpend(transactions, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun `траты за пределами периода не попадают в счёт`() {
        val transactions = listOf(
            tx("1", TransactionType.Expense, 4_000L, on = LocalDate.of(2026, 2, 28)),
            tx("2", TransactionType.Expense, 6_000L, on = LocalDate.of(2026, 3, 1)),
        )
        assertEquals(
            6_000L,
            FinanceAnalytics.totalSpend(transactions, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
        )
    }

    @Test
    fun `аномалия считается относительно собственной истории категории`() {
        assertTrue(FinanceAnalytics.isAnomalous(30_000L, listOf(10_000L, 12_000L, 11_000L)))
        assertFalse(FinanceAnalytics.isAnomalous(13_000L, listOf(10_000L, 12_000L, 11_000L)))
    }

    @Test
    fun `без истории аномалии не бывает`() {
        assertFalse(FinanceAnalytics.isAnomalous(30_000L, listOf(10_000L)))
        assertFalse(FinanceAnalytics.isAnomalous(30_000L, emptyList()))
    }

    @Test
    fun `дельта к прошлому периоду считается в долях`() {
        assertEquals(0.83, FinanceAnalytics.delta(18_300L, 10_000L)!!, 1e-9)
        assertNull(FinanceAnalytics.delta(18_300L, 0L))
    }
}
