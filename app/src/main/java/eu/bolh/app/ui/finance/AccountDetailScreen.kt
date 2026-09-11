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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.pluralStringResource
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
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.CurrencySheet
import eu.bolh.app.ui.common.Hint
import eu.bolh.app.ui.common.MetricChip
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyHero
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.Radius
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
    onAddAccount: () -> Unit,
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
    var currency by remember { mutableStateOf(state.baseCurrency) }
    var currencyPicker by remember { mutableStateOf(false) }

    if (currencyPicker) {
        CurrencySheet(
            current = currency,
            locale = locale,
            onDismiss = { currencyPicker = false },
            onSelect = {
                currency = it
                currencyPicker = false
            },
        )
    }
    // Выбор живёт на экране: он не переживает уход отсюда и ничего не значит
    // для данных — в отличие от самих операций.
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var confirming by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    // Строки берутся до списка: внутри LazyColumn композабл-вызовов уже нельзя.
    val expensesTitle = stringResource(R.string.account_expenses)
    val incomeTitle = stringResource(R.string.account_income)

    LaunchedEffect(account?.id, state.balanceMinor) {
        if (!editing) {
            name = account?.name.orEmpty()
            balance = Money.formatPlain(
                state.balanceMinor,
                Money.fractionDigitsOf(state.baseCurrency),
                locale,
            )
            currency = state.baseCurrency
        }
    }

    if (confirming) {
        ConfirmDeleteDialog(
            title = pluralStringResource(
                R.plurals.confirm_delete_transactions,
                selection.size,
                selection.size,
            ),
            onConfirm = {
                viewModel.deleteTransactions(selection)
                selection = emptySet()
            },
            onDismiss = { confirming = false },
        )
    }

    if (showSheet && account != null) {
        TransactionSheet(
            onDismiss = { showSheet = false },
            onAddAccount = {
                showSheet = false
                onAddAccount()
            },
            accountId = account.id,
        )
    }

    editingTx?.let { transaction ->
        TransactionSheet(
            onDismiss = { editingTx = null },
            onAddAccount = { editingTx = null },
            editing = transaction,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selecting) {
                            stringResource(R.string.tx_selected, selection.size)
                        } else {
                            account?.name.orEmpty()
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    Icon(
                        painter = painterResource(
                            if (selecting) R.drawable.ic_close else R.drawable.ic_arrow_back,
                        ),
                        contentDescription = null,
                        tint = status.textSecondary,
                        modifier = Modifier
                            .padding(start = Spacing.s)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (selecting) selection = emptySet() else onBack()
                            }
                            .padding(8.dp),
                    )
                },
                actions = {
                    if (selecting) {
                        Icon(
                            painter = painterResource(R.drawable.ic_select_all),
                            contentDescription = stringResource(R.string.tx_select_all),
                            tint = status.textSecondary,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selection = (state.expenses + state.income)
                                        .map { it.id }
                                        .toSet()
                                }
                                .padding(8.dp),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.common_delete),
                            tint = status.problem,
                            modifier = Modifier
                                .padding(end = Spacing.s)
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { confirming = true }
                                .padding(8.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            // Трату записывают, находясь внутри счёта, а не возвращаясь наружу.
            if (!selecting) {
                FloatingActionButton(
                    onClick = { showSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = stringResource(R.string.account_add_transaction),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (account == null) return@Scaffold

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
        ) {
            item(key = "balance") {
                // Сумма правится нажатием по самой сумме. Слово «Изменить» в углу
                // экрана искали дольше, чем меняли.
                BolhCard(
                    onClick = { editing = !editing },
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.s,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.account_balance_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textTertiary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Spacing.s),
                        )
                        Text(
                            text = stringResource(R.string.account_balance_edit_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_note),
                            contentDescription = stringResource(R.string.account_edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = Spacing.xs)
                                .size(18.dp),
                        )
                    }
                    Text(
                        text = Money.format(state.balanceMinor, state.baseCurrency, locale),
                        style = MoneyHero,
                        color = if (state.balanceMinor < 0L) {
                            status.problem
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs),
                    )
                }
            }

            if (editing) {
                item(key = "edit") {
                    BolhCard(
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.s,
                        ),
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
                            label = { Text(stringResource(R.string.account_new_balance)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.s),
                        )
                        PillButton(
                            text = "$currency · " + CurrencyPrefs.nameOf(currency, locale),
                            onClick = { currencyPicker = true },
                            icon = R.drawable.ic_chevron_down,
                            iconAtEnd = true,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                        // Остаток правится начальной суммой, а не подчисткой истории:
                        // операции остаются на месте, меняется точка отсчёта.
                        Hint(
                            text = stringResource(R.string.account_balance_note),
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            modifier = Modifier.padding(top = Spacing.m),
                        ) {
                            PillButton(
                                text = stringResource(R.string.account_save),
                                onClick = {
                                    viewModel.save(name, balance, currency)
                                    editing = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = stringResource(R.string.common_cancel),
                                onClick = { editing = false },
                                color = status.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            section(
                title = expensesTitle,
                total = state.expenseMinor,
                currency = state.baseCurrency,
                rows = state.expenses,
                categories = state.categoriesById,
                accent = status.textSecondary,
                selection = selection,
                selecting = selecting,
                onToggle = { selection = selection.toggle(it) },
                onLongClick = { selection = selection + it.id },
                onEdit = { editingTx = it },
            )

            section(
                title = incomeTitle,
                total = state.incomeMinor,
                currency = state.baseCurrency,
                rows = state.income,
                categories = state.categoriesById,
                accent = status.success,
                selection = selection,
                selecting = selecting,
                onToggle = { selection = selection.toggle(it) },
                onLongClick = { selection = selection + it.id },
                onEdit = { editingTx = it },
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
    selection: Set<String>,
    selecting: Boolean,
    onToggle: (String) -> Unit,
    onLongClick: (Transaction) -> Unit,
    onEdit: (Transaction) -> Unit,
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
                bottom = Spacing.s,
            ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = status.textSecondary,
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                text = Money.format(total, currency, locale),
                color = accent,
            )
        }
    }

    if (rows.isEmpty()) {
        item(key = "empty-$title") {
            BolhCard(
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.xs,
                ),
            ) {
                Text(
                    text = stringResource(R.string.tx_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalStatusColors.current.textTertiary,
                )
            }
        }
        return
    }

    items(rows.size, key = { "$title-" + rows[it].id }) { index ->
        val transaction = rows[index]
        val status = LocalStatusColors.current
        val locale = rememberLocale()
        val category = transaction.categoryId?.let { categories[it] }

        val selected = transaction.id in selection
        BolhCard(
            corner = Radius.block,
            accent = if (selected) MaterialTheme.colorScheme.primary else null,
            contentPadding = PaddingValues(
                horizontal = Spacing.l,
                vertical = Spacing.m,
            ),
            modifier = Modifier
                .padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.xs,
                )
                .tappable(
                    onClick = {
                        if (selecting) onToggle(transaction.id) else onEdit(transaction)
                    },
                    onLongClick = { onLongClick(transaction) },
                    longClickLabel = stringResource(R.string.common_delete),
                ),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(
                    if (selecting && selected) {
                        R.drawable.ic_check
                    } else {
                        BolhIcons.resFor(category?.icon ?: "payments")
                    },
                ),
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    status.textSecondary
                },
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
                    text = transaction.date.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_day_month), locale)),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                )
            }
            Text(
                text = Money.format(transaction.amountMinor, currency, locale),
                style = MoneyRow,
                color = accent,
            )
        }
        }
    }
}

@Composable
private fun categoryTitle(category: Category?): String {
    if (category == null) return stringResource(R.string.finance_uncategorised)
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}

/** Переключение выбора одной строки. */
private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id
