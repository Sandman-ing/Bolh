package eu.bolh.app.ui.finance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.R
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.model.TransactionType
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val accountsById: Map<String, Account> = emptyMap(),
    val baseCurrency: String = "EUR",
    val loaded: Boolean = false,
)

class TransactionsViewModel(private val finance: FinanceRepository) : ViewModel() {

    val state: StateFlow<TransactionsUiState> = combine(
        finance.observeTransactions(),
        finance.observeCategories(),
        finance.observeAccounts(),
    ) { transactions, categories, accounts ->
        TransactionsUiState(
            transactions = transactions,
            categoriesById = categories.associateBy { it.id },
            accountsById = accounts.associateBy { it.id },
            baseCurrency = finance.baseCurrency,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(),
    )

    fun delete(id: String) {
        viewModelScope.launch { finance.deleteTransaction(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                TransactionsViewModel(app.container.financeRepository)
            }
        }
    }
}

/**
 * История операций.
 *
 * Без неё запись траты — дорога в один конец: ошибся суммой и живи с этим.
 * Долгое нажатие по строке удаляет запись — так же, как отменяется срыв привычки.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    var pending by remember { mutableStateOf<Transaction?>(null) }

    pending?.let { transaction ->
        ConfirmDeleteDialog(
            title = stringResource(
                R.string.confirm_delete_transaction,
                Money.format(transaction.amountMinor, transaction.currencyCode, locale),
            ),
            onConfirm = { viewModel.delete(transaction.id) },
            onDismiss = { pending = null },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tx_list_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = status.textSecondary,
                        modifier = Modifier
                            .padding(start = Spacing.s)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .padding(8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        if (state.loaded && state.transactions.isEmpty()) {
            Text(
                text = stringResource(R.string.tx_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = status.textTertiary,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(Spacing.screenPadding),
            )
            return@Scaffold
        }

        val grouped = state.transactions.groupBy { it.date }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
        ) {
            grouped.forEach { (date, rows) ->
                item(key = "date-$date") {
                    Text(
                        text = dayLabel(date, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = status.textTertiary,
                        modifier = Modifier.padding(
                            start = Spacing.screenPadding,
                            top = Spacing.l,
                            bottom = Spacing.xs,
                        ),
                    )
                }
                items(rows.size, key = { rows[it].id }) { index ->
                    val transaction = rows[index]
                    TransactionRow(
                        transaction = transaction,
                        category = transaction.categoryId?.let { state.categoriesById[it] },
                        account = state.accountsById[transaction.accountId],
                        locale = locale,
                        onLongClick = { pending = transaction },
                    )
                    HorizontalDivider(
                        color = status.divider,
                        modifier = Modifier.padding(start = Spacing.screenPadding),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    account: Account?,
    locale: Locale,
    onLongClick: () -> Unit,
) {
    val status = LocalStatusColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.common_delete),
            )
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.financeRowVertical),
    ) {
        Icon(
            painter = painterResource(BolhIcons.resFor(category?.icon ?: "payments")),
            contentDescription = null,
            tint = status.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.m),
        ) {
            Text(
                text = transaction.note?.takeIf { it.isNotBlank() } ?: categoryLabel(category),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
            )
        }
        Text(
            text = signed(transaction, locale),
            style = MoneyRow,
            color = when (transaction.type) {
                TransactionType.Income -> status.success
                TransactionType.Transfer -> status.textSecondary
                TransactionType.Expense -> MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

private fun signed(transaction: Transaction, locale: Locale): String {
    val amount = Money.format(transaction.amountMinor, transaction.currencyCode, locale)
    return when (transaction.type) {
        TransactionType.Income -> "+$amount"
        TransactionType.Expense -> "−$amount"
        TransactionType.Transfer -> amount
    }
}

@Composable
private fun categoryLabel(category: Category?): String {
    if (category == null) return stringResource(R.string.finance_uncategorised)
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}

private fun dayLabel(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
