package eu.bolh.app.ui.finance

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.R
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.domain.model.AccountType
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.SelectChip
import eu.bolh.app.ui.common.CurrencySheet
import eu.bolh.app.ui.common.Hint
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.SectionTitle
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.common.tapIcon
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountEditorViewModel(private val finance: FinanceRepository) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(
        name: String,
        icon: String,
        type: AccountType,
        balanceText: String,
        currencyCode: String,
    ) {
        if (name.isBlank()) return
        // parseSignedMinor уже возвращает минорные единицы. Домножение на сто
        // раздувало начальный остаток ровно в сто раз, и счёт с самого начала
        // показывал не ту сумму. Ноль и минус допустимы: остаток бывает и таким.
        val minor = Money.parseSignedMinor(
            balanceText,
            Money.fractionDigitsOf(currencyCode),
        ) ?: 0L
        viewModelScope.launch {
            finance.createAccount(
                name = name,
                icon = icon,
                type = type,
                initialBalanceMinor = minor,
                currencyCode = currencyCode,
            )
            _saved.value = true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                AccountEditorViewModel(app.container.financeRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountEditorViewModel = viewModel(factory = AccountEditorViewModel.Factory),
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current

    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("credit_card") }
    var type by remember { mutableStateOf(AccountType.Card) }
    var balance by remember { mutableStateOf("") }

    val context = LocalContext.current
    val locale = rememberLocale()
    // Новый счёт начинает с валюты по умолчанию, но она не приговор:
    // Wise в евро и наличные в драмах — обычный случай, а не редкость.
    var currency by remember { mutableStateOf(CurrencyPrefs.get(context)) }
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

    LaunchedEffect(saved) { if (saved) onDone() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.account_new_title),
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
                            .tapIcon(onClick = onDone)
                            .padding(8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
                modifier = Modifier.padding(vertical = Spacing.s),
            ) {
                items(BolhIcons.account) { key ->
                    Icon(
                        painter = painterResource(BolhIcons.resFor(key)),
                        contentDescription = null,
                        tint = if (key == icon) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            status.textTertiary
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { icon = key }
                            .padding(10.dp),
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.account_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            ) {
                AccountType.entries.forEach { option ->
                    SelectChip(
                        text = stringResource(typeLabel(option)),
                        selected = type == option,
                        onClick = { type = option },
                    )
                }
            }

            SectionTitle(
                text = stringResource(R.string.account_currency),
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    top = Spacing.l,
                ),
            )
            PillButton(
                text = "$currency · " + CurrencyPrefs.nameOf(currency, locale),
                onClick = { currencyPicker = true },
                icon = R.drawable.ic_chevron_down,
                iconAtEnd = true,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            )
            Hint(
                text = stringResource(R.string.account_currency_note),
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.xs,
                ),
            )

            OutlinedTextField(
                value = balance,
                onValueChange = { balance = it },
                label = { Text(stringResource(R.string.account_balance_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
            )

            Button(
                onClick = { viewModel.save(name, icon, type, balance, currency) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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

private fun typeLabel(type: AccountType): Int = when (type) {
    AccountType.Cash -> R.string.account_type_cash
    AccountType.Card -> R.string.account_type_card
    AccountType.Bank -> R.string.account_type_bank
    AccountType.Savings -> R.string.account_type_savings
}
