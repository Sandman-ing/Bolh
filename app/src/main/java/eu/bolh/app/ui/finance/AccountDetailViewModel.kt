package eu.bolh.app.ui.finance

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    ) { accounts, transactions, categories ->
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
            baseCurrency = finance.baseCurrency,
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
        val desired = Money.parseSignedMinor(balanceText)

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
