package eu.bolh.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.ui.common.Money
import eu.bolh.app.data.repository.GoalRepository
import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.CategoryKind
import eu.bolh.app.domain.model.Goal
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionDraft(
    val type: TransactionType = TransactionType.Expense,
    val amountText: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val goalId: String? = null,
    val expanded: Boolean = false,
) {
    /**
     * Сумма в минорных единицах. Запятая и точка равноправны: человек вводит так,
     * как привык, а не так, как удобно парсеру.
     */
    val amountMinor: Long?
        get() {
            return Money.parseMinor(amountText)
        }

    val canSave: Boolean
        get() = amountMinor != null &&
            accountId != null &&
            (type != TransactionType.Transfer || (toAccountId != null && toAccountId != accountId))
}

data class TransactionEditorState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val goals: List<Goal> = emptyList(),
) {
    fun categoriesFor(type: TransactionType): List<Category> = when (type) {
        TransactionType.Income -> categories.filter { it.kind == CategoryKind.Income }
        else -> categories.filter { it.kind == CategoryKind.Expense }
    }
}

/**
 * Запись траты — самое частое действие в приложении, поэтому оно живёт
 * в нижней шторке, а не на отдельном экране (§6.9).
 */
class TransactionEditorViewModel(
    private val finance: FinanceRepository,
    goals: GoalRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow(TransactionDraft(date = finance.today()))
    val draft: StateFlow<TransactionDraft> = _draft.asStateFlow()

    val options: StateFlow<TransactionEditorState> = combine(
        finance.observeAccounts(),
        finance.observeCategories(),
        goals.observeActiveGoals(),
    ) { accounts, categories, goalList ->
        TransactionEditorState(accounts, categories, goalList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionEditorState(),
    )

    fun setType(type: TransactionType) = _draft.update {
        it.copy(type = type, categoryId = null, toAccountId = null)
    }

    fun setAmount(text: String) = _draft.update { it.copy(amountText = text) }

    fun setAccount(id: String) = _draft.update { it.copy(accountId = id) }

    fun setToAccount(id: String) = _draft.update { it.copy(toAccountId = id) }

    fun setCategory(id: String?) = _draft.update { it.copy(categoryId = id) }

    fun setDate(date: LocalDate) = _draft.update { it.copy(date = date) }

    fun setNote(text: String) = _draft.update { it.copy(note = text) }

    fun setGoal(id: String?) = _draft.update { it.copy(goalId = id) }

    fun toggleExpanded() = _draft.update { it.copy(expanded = !it.expanded) }

    fun reset() {
        _draft.value = TransactionDraft(
            date = finance.today(),
            accountId = options.value.accounts.firstOrNull()?.id,
        )
    }

    fun record(onDone: () -> Unit) {
        val draft = _draft.value
        val amount = draft.amountMinor ?: return
        val accountId = draft.accountId ?: return
        viewModelScope.launch {
            finance.record(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    toAccountId = draft.toAccountId.takeIf { draft.type == TransactionType.Transfer },
                    type = draft.type,
                    amountMinor = amount,
                    currencyCode = finance.baseCurrency,
                    // Базовая валюта к самой себе — единица, и она не меняется никогда.
                    rateToBase = 1.0,
                    date = draft.date,
                    categoryId = draft.categoryId.takeIf { draft.type != TransactionType.Transfer },
                    note = draft.note.takeIf { it.isNotBlank() },
                    goalId = draft.goalId,
                ),
            )
            reset()
            onDone()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                TransactionEditorViewModel(
                    finance = app.container.financeRepository,
                    goals = app.container.goalRepository,
                )
            }
        }
    }
}
