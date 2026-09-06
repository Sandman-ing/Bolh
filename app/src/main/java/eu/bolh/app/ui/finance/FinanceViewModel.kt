package eu.bolh.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.Contribution
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.domain.model.SavingsJar
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.templates.SystemCategories
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Переключатель «Круг / Список / Динамика» (§6.8). */
enum class FinanceView { Ring, List, Dynamics }

data class AccountBalance(val account: Account, val balanceMinor: Long)

data class CategorySpend(
    val category: Category?,
    val amountMinor: Long,
    val share: Double,
    val isAnomalous: Boolean,
    val deltaToPrevious: Double?,
)

data class JarRow(val jar: SavingsJar, val goal: Goal?, val currentMinor: Long)

data class MonthSpend(val month: YearMonth, val amountMinor: Long)

data class FinanceUiState(
    val accounts: List<AccountBalance> = emptyList(),
    val totalMinor: Long = 0L,
    val reservedMinor: Long = 0L,
    val monthSpendMinor: Long = 0L,
    val spendByCategory: List<CategorySpend> = emptyList(),
    val dynamics: List<MonthSpend> = emptyList(),
    val jars: List<JarRow> = emptyList(),
    val nextPayment: RecurringPayment? = null,
    val view: FinanceView = FinanceView.Ring,
    val hideObligatory: Boolean = false,
    val baseCurrency: String = "EUR",
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && accounts.isEmpty()

    /** Доступно потратить: остаток за вычетом того, что уже обещано целям. */
    val availableMinor: Long get() = totalMinor - reservedMinor
}

class FinanceViewModel(
    private val finance: FinanceRepository,
    private val goals: GoalRepository,
) : ViewModel() {

    private val view = MutableStateFlow(FinanceView.Ring)
    private val hideObligatory = MutableStateFlow(false)

    private val moneyFlow = combine(
        finance.observeAccounts(),
        finance.observeTransactions(),
        finance.observeCategories(),
        finance.observeJars(),
        finance.observeContributions(),
    ) { accounts, transactions, categories, jars, contributions ->
        MoneySnapshot(accounts, transactions, categories, jars, contributions)
    }

    private val extrasFlow = combine(
        finance.observeRecurring(),
        goals.observeAllGoals(),
        view,
        hideObligatory,
    ) { recurring, goalList, currentView, hidden ->
        Extras(recurring, goalList, currentView, hidden)
    }

    val state: StateFlow<FinanceUiState> = combine(moneyFlow, extrasFlow) { money, extras ->
        val today = finance.today()
        val month = YearMonth.from(today)
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()

        val categoriesById = money.categories.associateBy { it.id }
        val obligatoryIds = money.categories
            .filter { it.nameKey != null && it.nameKey in SystemCategories.obligatoryKeys }
            .map { it.id }
            .toSet()

        val visible = if (extras.hideObligatory) {
            money.transactions.filter { it.categoryId !in obligatoryIds }
        } else {
            money.transactions
        }

        val spend = FinanceAnalytics.spendByCategory(visible, monthStart, monthEnd)
        val monthTotal = spend.values.sum()

        FinanceUiState(
            accounts = money.accounts.map {
                AccountBalance(it, FinanceAnalytics.balanceOf(it, money.transactions))
            },
            totalMinor = FinanceAnalytics.totalBalance(money.accounts, money.transactions),
            reservedMinor = FinanceAnalytics.reservedInJars(money.jars, money.contributions),
            monthSpendMinor = monthTotal,
            spendByCategory = spend.entries
                .sortedByDescending { it.value }
                .map { (categoryId, amount) ->
                    CategorySpend(
                        category = categoriesById[categoryId],
                        amountMinor = amount,
                        share = if (monthTotal > 0L) amount.toDouble() / monthTotal else 0.0,
                        isAnomalous = FinanceAnalytics.isAnomalous(
                            currentMinor = amount,
                            historyMonthly = historyFor(categoryId, money.transactions, month),
                        ),
                        deltaToPrevious = FinanceAnalytics.delta(
                            currentMinor = amount,
                            previousMinor = spendIn(categoryId, money.transactions, month.minusMonths(1)),
                        ),
                    )
                },
            dynamics = (5 downTo 0).map { back ->
                val target = month.minusMonths(back.toLong())
                MonthSpend(
                    month = target,
                    amountMinor = FinanceAnalytics.totalSpend(
                        money.transactions,
                        target.atDay(1),
                        target.atEndOfMonth(),
                    ),
                )
            },
            jars = money.jars.map { jar ->
                JarRow(
                    jar = jar,
                    goal = extras.goals.firstOrNull { it.id == jar.goalId },
                    currentMinor = FinanceAnalytics.jarBalance(jar.id, money.contributions),
                )
            },
            nextPayment = extras.recurring.minByOrNull { it.nextDate },
            view = extras.view,
            hideObligatory = extras.hideObligatory,
            baseCurrency = finance.baseCurrency,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FinanceUiState(),
    )

    init {
        viewModelScope.launch {
            finance.ensureSystemCategories()
            finance.refreshSystemCategoryColors()
        }
    }

    fun setView(value: FinanceView) {
        view.value = value
    }

    fun toggleObligatory() {
        hideObligatory.value = !hideObligatory.value
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch { finance.deleteAccount(id) }
    }

    fun deleteRecurring(id: String) {
        viewModelScope.launch { finance.deleteRecurring(id) }
    }

    fun recordRecurring(paymentId: String) {
        viewModelScope.launch { finance.recordRecurring(paymentId) }
    }

    fun skipRecurring(paymentId: String) {
        viewModelScope.launch { finance.skipRecurring(paymentId) }
    }

    /** Три предыдущих месяца — база сравнения для аномалии (§6.8). */
    private fun historyFor(
        categoryId: String?,
        transactions: List<Transaction>,
        month: YearMonth,
    ): List<Long> = (1..3).map { back -> spendIn(categoryId, transactions, month.minusMonths(back.toLong())) }

    private fun spendIn(
        categoryId: String?,
        transactions: List<Transaction>,
        month: YearMonth,
    ): Long = FinanceAnalytics
        .spendByCategory(transactions, month.atDay(1), month.atEndOfMonth())[categoryId]
        ?: 0L

    private data class MoneySnapshot(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val categories: List<Category>,
        val jars: List<SavingsJar>,
        val contributions: Map<String, List<Contribution>>,
    )

    private data class Extras(
        val recurring: List<RecurringPayment>,
        val goals: List<Goal>,
        val view: FinanceView,
        val hideObligatory: Boolean,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                FinanceViewModel(
                    finance = app.container.financeRepository,
                    goals = app.container.goalRepository,
                )
            }
        }
    }
}
