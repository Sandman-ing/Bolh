package eu.bolh.app.ui.finance

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import eu.bolh.app.data.notifications.RecurringReminderScheduler
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.domain.model.Account
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.CategoryKind
import eu.bolh.app.domain.model.Recurrence
import eu.bolh.app.domain.model.RecurrenceUnit
import eu.bolh.app.domain.model.RecurringPayment
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringOptions(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

class RecurringEditorViewModel(
    private val finance: FinanceRepository,
    private val reminders: RecurringReminderScheduler,
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val options: StateFlow<RecurringOptions> = combine(
        finance.observeAccounts(),
        finance.observeCategories(),
    ) { accounts, categories ->
        RecurringOptions(accounts, categories.filter { it.kind == CategoryKind.Expense })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringOptions(),
    )

    fun save(
        name: String,
        amountMinor: Long,
        accountId: String,
        categoryId: String?,
        recurrence: Recurrence,
        firstDate: LocalDate,
    ) {
        if (name.isBlank() || amountMinor <= 0L) return
        viewModelScope.launch {
            val payment = RecurringPayment(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                amountMinor = amountMinor,
                currencyCode = finance.baseCurrency,
                accountId = accountId,
                categoryId = categoryId,
                recurrence = recurrence,
                nextDate = firstDate,
            )
            finance.createRecurring(payment)
            reminders.schedule(payment)
            _saved.value = true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                RecurringEditorViewModel(
                    finance = app.container.financeRepository,
                    reminders = app.container.recurringScheduler,
                )
            }
        }
    }
}

/**
 * Регулярный платёж настраивается вручную (§6.5). Уведомление придёт за день
 * до списания, и запись создастся только по нажатию — приложение не пишет
 * в историю то, чего человек не подтвердил.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringEditorViewModel = viewModel(factory = RecurringEditorViewModel.Factory),
) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val locale = rememberLocale()

    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var unit by remember { mutableStateOf(RecurrenceUnit.Month) }
    var interval by remember { mutableStateOf(1) }
    var firstDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }

    LaunchedEffect(saved) { if (saved) onDone() }
    LaunchedEffect(options.accounts) {
        if (accountId == null) accountId = options.accounts.firstOrNull()?.id
    }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.recurring_new_title),
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
                            .clickable(onClick = onDone)
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.recurring_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.tx_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
            )

            Label(stringResource(R.string.tx_account))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
            ) {
                items(options.accounts, key = { it.id }) { account ->
                    Chip(
                        text = account.name,
                        selected = accountId == account.id,
                        onClick = { accountId = account.id },
                        icon = account.icon,
                    )
                }
            }

            Label(stringResource(R.string.tx_category))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
            ) {
                items(options.categories, key = { it.id }) { category ->
                    val res = SystemCategories.nameResFor(category.nameKey)
                    Chip(
                        text = if (res != null) stringResource(res) else category.name,
                        selected = categoryId == category.id,
                        onClick = { categoryId = if (categoryId == category.id) null else category.id },
                        icon = category.icon,
                        accent = Color(category.colorArgb),
                    )
                }
            }

            Label(stringResource(R.string.recurring_every))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            ) {
                RecurrenceUnit.entries.forEach { option ->
                    Chip(
                        text = stringResource(unitLabel(option)),
                        selected = unit == option,
                        onClick = { unit = option },
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.s,
                ),
            ) {
                Text(
                    text = stringResource(R.string.recurring_interval),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { interval = (interval - 1).coerceAtLeast(1) }) {
                    Text("−")
                }
                Text(
                    text = interval.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = Spacing.s),
                )
                TextButton(onClick = { interval = (interval + 1).coerceAtMost(24) }) {
                    Text("+")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> firstDate = LocalDate.of(year, month + 1, day) },
                            firstDate.year,
                            firstDate.monthValue - 1,
                            firstDate.dayOfMonth,
                        ).show()
                    }
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            ) {
                Text(
                    text = stringResource(R.string.recurring_first_date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = firstDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = {
                    val minor = amount.replace(',', '.').replace(" ", "").toDoubleOrNull()
                        ?.let { Math.round(it * 100.0) } ?: 0L
                    accountId?.let { account ->
                        viewModel.save(
                            name = name,
                            amountMinor = minor,
                            accountId = account,
                            categoryId = categoryId,
                            recurrence = Recurrence(unit, interval),
                            firstDate = firstDate,
                        )
                    }
                },
                enabled = name.isNotBlank() && amount.isNotBlank() && accountId != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Ink,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
            ) {
                Text(stringResource(R.string.account_save))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalStatusColors.current.textTertiary,
        modifier = Modifier.padding(
            start = Spacing.screenPadding,
            top = Spacing.m,
            bottom = Spacing.xs,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: String? = null,
    accent: Color? = null,
) {
    val status = LocalStatusColors.current
    val selectedColor = accent ?: MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let {
            {
                Icon(
                    painter = painterResource(BolhIcons.resFor(it)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = status.textSecondary,
            selectedContainerColor = selectedColor,
            selectedLabelColor = Ink,
            selectedLeadingIconColor = Ink,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = status.divider,
            selectedBorderColor = selectedColor,
        ),
    )
}

private fun unitLabel(unit: RecurrenceUnit): Int = when (unit) {
    RecurrenceUnit.Day -> R.string.recurring_period_day
    RecurrenceUnit.Week -> R.string.recurring_period_week
    RecurrenceUnit.Month -> R.string.recurring_period_month
    RecurrenceUnit.Year -> R.string.recurring_period_year
}
