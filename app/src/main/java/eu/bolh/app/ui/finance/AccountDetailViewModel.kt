package eu.bolh.app.ui.finance

import eu.bolh.app.domain.analytics.CurrencyMath
import eu.bolh.app.data.rates.RatesSnapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.ui.common.Money
import eu.bolh.app.domain.analytics.FinanceAnalytics
import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountDetailUiState(
    val account: Account? = null,
    val balanceMinor: Long = 0L,
    val expenses: List<Transaction> = emptyList(),
    val income: List<Transaction> = emptyList(),
    val expenseMinor: Long = 0L,
    val incomeMinor: Long = 0L,
    val categoriesById: Map<String, Category> = emptyMap(),
    val baseCurrency: String = "EUR",
)

class AccountDetailViewModel(
    private val finance: FinanceRepository,
    private val accountId: String,
) : ViewModel() {

    val state: StateFlow<AccountDetailUiState> = combine(
        finance.observeAccounts(),
        finance.observeTransactions(),
        finance.observeCategories(),
        finance.currency,
    ) { accounts, transactions, categories, currency ->
        val account = accounts.firstOrNull { it.id == accountId }
            ?: return@combine AccountDetailUiState(categoriesById = categories.associateBy { it.id })

        val mine = transactions.filter {
            it.accountId == accountId || it.toAccountId == accountId
        }
        val expenses = mine.filter {
            it.type == TransactionType.Expense ||
                (it.type == TransactionType.Transfer && it.accountId == accountId)
        }
        val income = mine.filter {
            it.type == TransactionType.Income ||
                (it.type == TransactionType.Transfer && it.toAccountId == accountId)
        }

        AccountDetailUiState(
            // Валюта счёта, а не приложения: внутри счёта всё считается в ней.
            baseCurrency = account.currencyCode.ifBlank { currency },
            account = account,
            balanceMinor = FinanceAnalytics.balanceOf(account, transactions),
            expenses = expenses,
            income = income,
            expenseMinor = expenses.sumOf { it.amountMinor },
            incomeMinor = income.sumOf { it.amountMinor },
            categoriesById = categories.associateBy { it.id },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountDetailUiState(),
    )

    /**
     * Правка счёта. Остаток задаётся желаемым числом, а сходится он сдвигом
     * начальной суммы: история операций при этом не трогается — иначе цифра
     * сошлась бы ценой вранья в прошлом.
     */
    fun save(name: String, balanceText: String) {
        val account = state.value.account ?: return
        val desired = Money.parseSignedMinor(
            balanceText,
            Money.fractionDigitsOf(account.currencyCode),
        )

        viewModelScope.launch {
            finance.updateAccount(
                accountId = account.id,
                name = name.trim().ifBlank { account.name },
                initialBalanceMinor = if (desired == null) {
                    account.initialBalanceMinor
                } else {
                    account.initialBalanceMinor + (desired - state.value.balanceMinor)
                },
            )
        }
    }

    /**
     * Последние известные курсы: из них считается, сколько будет в другой валюте.
     * Экран обязан их собирать: без подписчика поток не запускается, и прежде
     * предпросмотр пересчёта всегда говорил, что курса нет.
     */
    val rates: StateFlow<RatesSnapshot?> = finance.observeRates().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _ratesLoading = MutableStateFlow(false)
    val ratesLoading: StateFlow<Boolean> = _ratesLoading.asStateFlow()

    /**
     * Курса для пересчёта нет: загрузить его сейчас, не дожидаясь обновления
     * раз в полсуток. После восстановления копии, например, курсов нет вовсе.
     */
    fun loadRates() {
        if (_ratesLoading.value) return
        _ratesLoading.value = true
        viewModelScope.launch {
            try {
                runCatching { finance.refreshRatesIfStale(maxAgeMillis = 0L) }
            } finally {
                _ratesLoading.value = false
            }
        }
    }

    /** Остаток счёта в другой валюте по курсам [snapshot], если курс известен. */
    fun convertedBalance(to: String, snapshot: RatesSnapshot?): Long? {
        val account = state.value.account ?: return null
        val perEur = snapshot?.perEur ?: return null
        return CurrencyMath.convert(state.value.balanceMinor, account.currencyCode, to, perEur)
    }

    /** Смена валюты счёта; [onResult] получает false, если курс так и не нашёлся. */
    fun changeCurrency(to: String, convert: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = finance.changeAccountCurrency(accountId, to, convert)
            onResult(result == FinanceRepository.CurrencyChange.Done)
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { finance.deleteTransaction(id) }
    }

    /** Удаление пачкой: разбирать список по одной строке — работа, а не решение. */
    fun deleteTransactions(ids: Set<String>) {
        viewModelScope.launch { ids.forEach { finance.deleteTransaction(it) } }
    }

    companion object {
        const val ARG_ACCOUNT_ID = "accountId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                AccountDetailViewModel(
                    finance = app.container.financeRepository,
                    accountId = handle.get<String>(ARG_ACCOUNT_ID).orEmpty(),
                )
            }
        }
    }
}
