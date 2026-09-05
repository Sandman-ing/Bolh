package eu.bolh.app.ui.finance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.Transaction
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyLarge
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/**
 * Счёт изнутри: сначала сам счёт, под ним — расходы и доходы двумя разделами.
 *
 * Общий экран финансов отвечает на вопрос «сколько всего», этот — «куда с этого
 * счёта уходит и откуда приходит».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountDetailViewModel = viewModel(factory = AccountDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val account = state.account

    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Transaction?>(null) }
    // Строки берутся до списка: внутри LazyColumn композабл-вызовов уже нельзя.
    val expensesTitle = stringResource(R.string.account_expenses)
    val incomeTitle = stringResource(R.string.account_income)

    LaunchedEffect(account?.id, state.balanceMinor) {
        if (!editing) {
            name = account?.name.orEmpty()
            balance = Money.formatPlain(state.balanceMinor, locale = locale)
        }
    }

    pending?.let { transaction ->
        ConfirmDeleteDialog(
            title = stringResource(
                R.string.confirm_delete_transaction,
                Money.format(transaction.amountMinor, transaction.currencyCode, locale),
            ),
            onConfirm = { viewModel.deleteTransaction(transaction.id) },
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
                        text = account?.name.orEmpty(),
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
                actions = {
                    TextButton(onClick = { editing = !editing }) {
                        Text(stringResource(R.string.account_edit))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        if (account == null) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
        ) {
            item(key = "balance") {
                Text(
                    text = Money.format(state.balanceMinor, account.currencyCode, locale),
                    style = MoneyLarge,
                    color = if (state.balanceMinor < 0L) {
                        status.problem
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.padding(
                        start = Spacing.screenPadding,
                        end = Spacing.screenPadding,
                        bottom = Spacing.m,
                    ),
                )
            }

            if (editing) {
                item(key = "edit") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Spacing.screenPadding)
                            .clip(RoundedCornerShape(14.dp))
                            .background(status.surfaceRaised)
                            .padding(Spacing.l),
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.account_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = balance,
                            onValueChange = { balance = it },
                            label = { Text(stringResource(R.string.account_balance_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.s),
                        )
                        // Остаток правится начальной суммой, а не подчисткой истории:
                        // операции остаются на месте, меняется точка отсчёта.
                        Text(
                            text = stringResource(R.string.account_balance_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textTertiary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Row(modifier = Modifier.padding(top = Spacing.s)) {
                            TextButton(
                                onClick = {
                                    viewModel.save(name, balance)
                                    editing = false
                                },
                            ) {
                                Text(stringResource(R.string.account_save))
                            }
                            TextButton(onClick = { editing = false }) {
                                Text(
                                    text = stringResource(R.string.common_cancel),
                                    color = status.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            section(
                title = expensesTitle,
                total = state.expenseMinor,
                currency = account.currencyCode,
                rows = state.expenses,
                categories = state.categoriesById,
                accent = status.textSecondary,
                onLongClick = { pending = it },
            )

            section(
                title = incomeTitle,
                total = state.incomeMinor,
                currency = account.currencyCode,
                rows = state.income,
                categories = state.categoriesById,
                accent = status.success,
                onLongClick = { pending = it },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    total: Long,
    currency: String,
    rows: List<Transaction>,
    categories: Map<String, Category>,
    accent: androidx.compose.ui.graphics.Color,
    onLongClick: (Transaction) -> Unit,
) {
    item(key = "header-$title") {
        val status = LocalStatusColors.current
        val locale = rememberLocale()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = Spacing.screenPadding,
                end = Spacing.screenPadding,
                top = Spacing.xl,
                bottom = Spacing.xs,
            ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = status.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(total, currency, locale),
                style = MoneyRow,
                color = accent,
            )
        }
    }

    if (rows.isEmpty()) {
        item(key = "empty-$title") {
            Text(
                text = stringResource(R.string.tx_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalStatusColors.current.textTertiary,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            )
        }
        return
    }

    items(rows.size, key = { "$title-" + rows[it].id }) { index ->
        val transaction = rows[index]
        val status = LocalStatusColors.current
        val locale = rememberLocale()
        val category = transaction.categoryId?.let { categories[it] }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongClick(transaction) },
                    onLongClickLabel = stringResource(R.string.common_delete),
                )
                .padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.financeRowVertical,
                ),
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
                    text = transaction.note?.takeIf { it.isNotBlank() }
                        ?: categoryTitle(category),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = transaction.date.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                )
            }
            Text(
                text = Money.format(transaction.amountMinor, transaction.currencyCode, locale),
                style = MoneyRow,
                color = accent,
            )
        }
        HorizontalDivider(
            color = status.divider,
            modifier = Modifier.padding(start = Spacing.screenPadding),
        )
    }
}

@Composable
private fun categoryTitle(category: Category?): String {
    if (category == null) return stringResource(R.string.finance_uncategorised)
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}
