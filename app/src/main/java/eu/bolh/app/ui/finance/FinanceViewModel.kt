package eu.bolh.app.ui.finance

import eu.bolh.app.domain.analytics.CurrencyMath
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

/**
 * Примерный итог по всем счетам в одной валюте (§6.3).
 *
 * [missing] — валюты, для которых курса ещё нет: сложить их честно нельзя,
 * и об этом говорится прямо, а не молча.
 */
data class ApproxTotal(
    val minor: Long,
    val currency: String,
    val ratesDate: LocalDate?,
    val missing: List<String>,
)

data class FinanceUiState(
    val accounts: List<AccountBalance> = emptyList(),
    val totalMinor: Long = 0L,
    val reservedMinor: Long = 0L,
    val periodTotalMinor: Long = 0L,
    val spendByCategory: List<CategorySpend> = emptyList(),
    val dynamics: List<MonthSpend> = emptyList(),
    val jars: List<JarRow> = emptyList(),
    val nextPayment: RecurringPayment? = null,
    /** Все регулярные платежи по дате ближайшего списания. */
    val payments: List<RecurringPayment> = emptyList(),
    val view: FinanceView = FinanceView.Ring,
    val flow: MoneyFlow = MoneyFlow.Expense,
    val period: FinancePeriod = FinancePeriod.Month,
    /** Пусто — смотрим все счета сразу. */
    val selectedAccountId: String? = null,
    val hideObligatory: Boolean = false,
    val baseCurrency: String = "EUR",
    /** Валюты, встречающиеся среди счетов. Одна — выбирать нечего. */
    val currencies: List<String> = emptyList(),
    /**
     * Валюта, в которой сейчас считается разбор.
     *
     * Разбор по курсу не пересчитывается: курс меняется каждый день, а
     * записанные суммы — нет, и кольцо из пересчитанных драмов врало бы.
     * Поэтому итог, кольцо и категории показывают одну валюту за раз, список
     * счетов — все, каждый в своей, а общий итог по курсу стоит отдельной
     * строкой и помечен как примерный (§6.3).
     */
    val analysedCurrency: String = "EUR",
    /** Примерный общий итог — только когда счета в разных валютах. */
    val approxTotal: ApproxTotal? = null,
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
    private val analysedCurrency = MutableStateFlow<String?>(null)

    /** Счёт и валюта ходят парой: выбранный счёт задаёт валюту разбора сам. */
    private val scopeFlow = combine(selectedAccount, analysedCurrency) { id, code ->
        id to code
    }

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
        scopeFlow,
    ) { currentView, hidden, currentFlow, currentPeriod, scope ->
        Choices(currentView, hidden, currentFlow, currentPeriod, scope.first, scope.second)
    }

    private val extrasFlow = combine(
        finance.observeRecurring(),
        goals.observeAllGoals(),
        choicesFlow,
    ) { recurring, goalList, choices ->
        Extras(recurring, goalList, choices)
    }

    val state: StateFlow<FinanceUiState> = combine(
        moneyFlow,
        extrasFlow,
        finance.currency,
        finance.observeRates(),
    ) { money, extras, currency, rates ->
        val today = finance.today()
        val month = YearMonth.from(today)
        val choices = extras.choices
        val (rangeStart, rangeEnd) = choices.period.rangeAt(today)

        // Валюты счетов, частая — первой: по ней и считаем, пока не сказано иначе.
        val currencies = money.accounts
            .map { it.currencyCode }
            .distinct()
            .sortedByDescending { code -> money.accounts.count { it.currencyCode == code } }
        val chosenAccount = money.accounts.firstOrNull { it.id == choices.accountId }
        val analysed = chosenAccount?.currencyCode
            ?: choices.currency?.takeIf { it in currencies }
            ?: currency.takeIf { it in currencies }
            ?: currencies.firstOrNull()
            ?: currency
        val accountsHere = money.accounts.filter { it.currencyCode == analysed }
        val idsHere = accountsHere.map { it.id }.toSet()
        // Операции чужих валют в разбор не идут вовсе: без курса их сумма
        // была бы просто сложением разных единиц.
        val txHere = money.transactions.filter { it.accountId in idsHere }

        // Примерный итог нужен, только когда валют больше одной: в одной
        // валюте точная сумма уже стоит в кольце, и «примерно» только сбивало бы.
        val approx = if (currencies.size > 1) {
            val perEur = rates?.perEur.orEmpty()
            var sum = 0L
            val missing = mutableListOf<String>()
            money.accounts.forEach { account ->
                val balance = FinanceAnalytics.balanceOf(account, money.transactions)
                val converted = CurrencyMath.convert(balance, account.currencyCode, currency, perEur)
                if (converted == null) {
                    if (account.currencyCode !in missing) missing += account.currencyCode
                } else {
                    sum += converted
                }
            }
            ApproxTotal(sum, currency, rates?.date, missing)
        } else {
            null
        }

        val categoriesById = money.categories.associateBy { it.id }
        val obligatoryIds = money.categories
            .filter { it.nameKey != null && it.nameKey in SystemCategories.obligatoryKeys }
            .map { it.id }
            .toSet()

        // Выбранный счёт сужает разбор, но не остаток: остаток берётся отдельно.
        val ofAccount = choices.accountId
            ?.let { id -> txHere.filter { it.accountId == id } }
            ?: txHere

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
            totalMinor = FinanceAnalytics.totalBalance(accountsHere, txHere),
            reservedMinor = FinanceAnalytics.reservedInJars(
                money.jars.filter { it.currencyCode == analysed },
                money.contributions,
            ),
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
                            historyMonthly = historyFor(categoryId, txHere, month),
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
                                    txHere,
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
                        txHere,
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
            payments = extras.recurring.sortedBy { it.nextDate },
            view = choices.view,
            flow = choices.flow,
            period = choices.period,
            selectedAccountId = choices.accountId
                ?.takeIf { id -> money.accounts.any { it.id == id } },
            hideObligatory = choices.hideObligatory,
            baseCurrency = currency,
            currencies = currencies,
            analysedCurrency = analysed,
            approxTotal = approx,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FinanceUiState(),
    )

    init {
        // Курсы — отдельной задачей: без сети она просто ничего не сделает и не
        // задержит заведение готовых категорий.
        viewModelScope.launch { finance.refreshRatesIfStale() }
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

    /** В какой валюте считать разбор, когда счетов несколько и они разные. */
    fun selectCurrency(code: String) {
        analysedCurrency.value = code
        // Выбранный счёт задавал бы валюту сам и перебивал бы выбор.
        selectedAccount.value = null
    }

    /** Копилка без цели — сирота: удалить её больше нечем, кроме как здесь. */
    fun deleteJar(id: String) {
        viewModelScope.launch { finance.deleteJar(id) }
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
        val currency: String?,
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
