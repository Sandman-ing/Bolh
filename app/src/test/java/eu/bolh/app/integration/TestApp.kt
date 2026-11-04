package eu.bolh.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.bolh.app.BolhContainer
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.Recurrence
import eu.bolh.app.domain.model.RecurrenceUnit
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Приложение в тесте: тот же контейнер зависимостей и та же база Room, что
 * на телефоне, только внутри Robolectric.
 *
 * База — синглтон на весь процесс тестов, поэтому каждый тест начинает с
 * чистой: данные прошлого теста не должны протекать в следующий.
 */
class TestApp {

    val container = BolhContainer(ApplicationProvider.getApplicationContext<Context>())

    val goals get() = container.goalRepository
    val habits get() = container.habitRepository
    val finance get() = container.financeRepository

    init {
        runBlocking(Dispatchers.IO) { container.database.clearAllTables() }
        finance.baseCurrency = "EUR"
    }

    fun goal(name: String, deadline: LocalDate? = null): String = runBlocking {
        val id = UUID.randomUUID().toString()
        goals.create(Goal(id, name, "flag", LocalDate.of(2026, 9, 1), deadline))
        id
    }

    fun expense(account: String, amountMinor: Long, currency: String = "EUR"): Transaction =
        Transaction(
            id = UUID.randomUUID().toString(),
            accountId = account,
            type = TransactionType.Expense,
            amountMinor = amountMinor,
            currencyCode = currency,
            rateToBase = 1.0,
            date = finance.today(),
        )

    fun transfer(from: String, to: String, amountMinor: Long): Transaction =
        expense(from, amountMinor).copy(type = TransactionType.Transfer, toAccountId = to)

    fun payment(account: String, name: String, once: Boolean = false): RecurringPayment =
        RecurringPayment(
            id = UUID.randomUUID().toString(),
            name = name,
            amountMinor = 2_000L,
            currencyCode = "EUR",
            accountId = account,
            recurrence = Recurrence(RecurrenceUnit.Month, once = once),
            nextDate = finance.today().plusDays(7),
        )
}
