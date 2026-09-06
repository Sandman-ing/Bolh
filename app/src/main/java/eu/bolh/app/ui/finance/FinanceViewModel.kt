package eu.bolh.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.prefs.SettingsStore
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
import eu.bolh.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Переключатель «Круг / Список / Динамика» (§6.8). */
enum class FinanceView { Ring, List, Dynamics }

/** Что разбираем: куда ушло или откуда пришло. */
enum class MoneyFlow { Expense, Income }

/**
 * Период разбора. Месяц остаётся значением по умолчанию: сравнение с прошлым
 * и признак аномалии (§6.8) определены помесячно и в других периодах не считаются.
 */
enum class FinancePeriod {
    Day, Week, Month, Year;

    fun rangeAt(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        Day -> today to today
        Week -> {
            val start = today.with(WeekFields.ISO.dayOfWeek(), 1L)
            start to start.plusDays(6)
        }
        Month -> YearMonth.from(today).let { it.atDay(1) to it.atEndOfMonth() }
        Year -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
    }
}

data class AccountBalance(val account: Account, val balanceMinor: Long)

data class CategorySpend(
    val category: Category?,
    val amountMinor: Long,
    val share: Double,
    val isAnomalous: Boolean,
    val deltaToPrevious: Double?,
    /** Месячный лимит категории — только если человек его поставил (§6.7). */
    val plannedMinor: Long? = null,
) {
    val overPlan: Boolean get() = plannedMinor?.let { amountMinor > it } == true

    /** Девять десятых лимита — ещё не перебор, но уже стоит знать. */
    val nearPlan: Boolean
        get() = plannedMinor?.let { amountMinor >= it * 9 / 10 && amountMinor <= it } == true
}

data class JarRow(val jar: SavingsJar, val goal: Goal?, val currentMinor: Long)

data class MonthSpend(val month: YearMonth, val amountMinor: Long)

data class FinanceUiState(
    val accounts: List<AccountBalance> = emptyList(),
    val totalMinor: Long = 0L,
    val reservedMinor: Long = 0L,
    val periodTotalMinor: Long = 0L,
    val spendByCategory: List<CategorySpend> = emptyList(),
    val dynamics: List<MonthSpend> = emptyList(),
    val jars: List<JarRow> = emptyList(),
    val nextPayment: RecurringPayment? = null,
    val view: FinanceView = FinanceView.Ring,
    val flow: MoneyFlow = MoneyFlow.Expense,
    val period: FinancePeriod = FinancePeriod.Month,
    /** Пусто — смотрим все счета сразу. */
    val selectedAccountId: String? = null,
    val hideObligatory: Boolean = false,
    val baseCurrency: String = "EUR",
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && accounts.isEmpty()

    /** Доступно потратить: остаток за вычетом того, что уже обещано целям. */
    val availableMinor: Long get() = totalMinor - reservedMinor

    val selectedAccount: AccountBalance?
        get() = accounts.firstOrNull { it.account.id == selectedAccountId }

    /** Главное число шапки: либо всё вместе, либо выбранный счёт. */
    val headlineMinor: Long get() = selectedAccount?.balanceMinor ?: totalMinor
}

class FinanceViewModel(
    private val finance: FinanceRepository,
    private val goals: GoalRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val view = MutableStateFlow(FinanceView.Ring)
    private val hideObligatory = MutableStateFlow(false)
    private val flow = MutableStateFlow(MoneyFlow.Expense)
    private val period = MutableStateFlow(FinancePeriod.Month)
    private val selectedAccount = MutableStateFlow<String?>(null)

    private val moneyFlow = combine(
        finance.observeAccounts(),
        finance.observeTransactions(),
        finance.observeCategories(),
        finance.observeJars(),
        finance.observeContributions(),
    ) { accounts, transactions, categories, jars, contributions ->
        MoneySnapshot(accounts, transactions, categories, jars, contributions)
    }

    private val choicesFlow = combine(
        view,
        hideObligatory,
        flow,
        period,
        selectedAccount,
    ) { currentView, hidden, currentFlow, currentPeriod, accountId ->
        Choices(currentView, hidden, currentFlow, currentPeriod, accountId)
    }

    private val extrasFlow = combine(
        finance.observeRecurring(),
        goals.observeAllGoals(),
        choicesFlow,
    ) { recurring, goalList, choices ->
        Extras(recurring, goalList, choices)
    }

    val state: StateFlow<FinanceUiState> = combine(moneyFlow, extrasFlow) { money, extras ->
        val today = finance.today()
        val month = YearMonth.from(today)
        val choices = extras.choices
        val (rangeStart, rangeEnd) = choices.period.rangeAt(today)

        val categoriesById = money.categories.associateBy { it.id }
        val obligatoryIds = money.categories
            .filter { it.nameKey != null && it.nameKey in SystemCategories.obligatoryKeys }
            .map { it.id }
            .toSet()

        // Выбранный счёт сужает разбор, но не остаток: остаток берётся отдельно.
        val ofAccount = choices.accountId
            ?.let { id -> money.transactions.filter { it.accountId == id } }
            ?: money.transactions

        val visible = if (choices.hideObligatory && choices.flow == MoneyFlow.Expense) {
            ofAccount.filter { it.categoryId !in obligatoryIds }
        } else {
            ofAccount
        }

        val type = when (choices.flow) {
            MoneyFlow.Expense -> TransactionType.Expense
            MoneyFlow.Income -> TransactionType.Income
        }
        val spend = FinanceAnalytics.byCategory(visible, rangeStart, rangeEnd, type)
        val periodTotal = spend.values.sum()
        // Сравнение с прошлым и аномалия определены помесячно: в остальных
        // периодах их честнее не показывать, чем показывать неверно.
        val comparable = choices.period == FinancePeriod.Month &&
            choices.flow == MoneyFlow.Expense

        FinanceUiState(
            accounts = money.accounts.map {
                AccountBalance(it, FinanceAnalytics.balanceOf(it, money.transactions))
            },
            totalMinor = FinanceAnalytics.totalBalance(money.accounts, money.transactions),
            reservedMinor = FinanceAnalytics.reservedInJars(money.jars, money.contributions),
            periodTotalMinor = periodTotal,
            spendByCategory = spend.entries
                .sortedByDescending { it.value }
                .map { (categoryId, amount) ->
                    CategorySpend(
                        category = categoriesById[categoryId],
                        amountMinor = amount,
                        share = if (periodTotal > 0L) amount.toDouble() / periodTotal else 0.0,
                        isAnomalous = comparable && FinanceAnalytics.isAnomalous(
                            currentMinor = amount,
                            historyMonthly = historyFor(categoryId, money.transactions, month),
                        ),
                        // Лимит помесячный, поэтому и сравнивать его можно
                        // только с месяцем: за день или год «из 300 €» соврёт.
                        plannedMinor = categoriesById[categoryId]
                            ?.plannedMinor
                            ?.takeIf { choices.period == FinancePeriod.Month },
                        deltaToPrevious = if (!comparable) {
                            null
                        } else {
                            FinanceAnalytics.delta(
                                currentMinor = amount,
                                previousMinor = spendIn(
                                    categoryId,
                                    money.transactions,
                                    month.minusMonths(1),
                                ),
                            )
                        },
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
            view = choices.view,
            flow = choices.flow,
            period = choices.period,
            selectedAccountId = choices.accountId
                ?.takeIf { id -> money.accounts.any { it.id == id } },
            hideObligatory = choices.hideObligatory,
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
            // Палитра догоняется один раз: дальше цвет принадлежит человеку.
            if (settings.paletteVersion.first() < PALETTE_VERSION) {
                finance.refreshSystemCategoryColors()
                settings.setPaletteVersion(PALETTE_VERSION)
            }
        }
    }

    fun setView(value: FinanceView) {
        view.value = value
    }

    fun toggleObligatory() {
        hideObligatory.value = !hideObligatory.value
    }

    fun setFlow(value: MoneyFlow) {
        flow.value = value
    }

    fun setPeriod(value: FinancePeriod) {
        period.value = value
    }

    /** Пусто — смотреть все счета сразу. */
    fun selectAccount(id: String?) {
        selectedAccount.value = id
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

    private data class Choices(
        val view: FinanceView,
        val hideObligatory: Boolean,
        val flow: MoneyFlow,
        val period: FinancePeriod,
        val accountId: String?,
    )

    private data class Extras(
        val recurring: List<RecurringPayment>,
        val goals: List<Goal>,
        val choices: Choices,
    )

    companion object {
        /** Поднимается, когда меняется палитра готовых категорий. */
        private const val PALETTE_VERSION = 2

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                FinanceViewModel(
                    finance = app.container.financeRepository,
                    goals = app.container.goalRepository,
                    settings = app.container.settings,
                )
            }
        }
    }
}
