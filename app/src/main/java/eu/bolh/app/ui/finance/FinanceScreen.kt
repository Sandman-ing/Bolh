package eu.bolh.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.EmptyState
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.finance.components.DonutChart
import eu.bolh.app.ui.finance.components.DonutSlice
import eu.bolh.app.ui.theme.GoalsTeal
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyLarge
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Экран финансов — приборная панель (§9.2): строки-выписка, моноширинные цифры,
 * тонкие разделители. Читается как банковская выписка, что для денег уместно.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FinanceScreen(
    onAddAccount: () -> Unit,
    onAddTransaction: () -> Unit,
    onAddRecurring: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel = viewModel(factory = FinanceViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    var pendingAccount by remember { mutableStateOf<String?>(null) }
    var pendingPayment by remember { mutableStateOf<String?>(null) }

    pendingAccount?.let { id ->
        val account = state.accounts.firstOrNull { it.account.id == id }?.account
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete, account?.name.orEmpty()),
            onConfirm = { viewModel.deleteAccount(id) },
            onDismiss = { pendingAccount = null },
        )
    }
    pendingPayment?.let { id ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete, state.nextPayment?.name.orEmpty()),
            onConfirm = { viewModel.deleteRecurring(id) },
            onDismiss = { pendingPayment = null },
        )
    }

    if (state.isEmpty) {
        EmptyState(
            action = stringResource(R.string.finance_empty_action),
            example = stringResource(R.string.finance_empty_example),
            onAction = onAddAccount,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
    ) {
        item(key = "total") {
            Column(
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.l,
                ),
            ) {
                Text(
                    text = stringResource(R.string.finance_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                )
                Text(
                    text = Money.format(state.totalMinor, state.baseCurrency, locale),
                    style = MoneyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                // Без этой строки человек считает своими деньги, уже обещанные цели.
                if (state.reservedMinor > 0L) {
                    Text(
                        text = stringResource(
                            R.string.finance_reserved,
                            Money.format(state.reservedMinor, state.baseCurrency, locale),
                        ),
                        style = NumberSmall,
                        color = GoalsTeal,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = stringResource(
                            R.string.finance_available,
                            Money.format(state.availableMinor, state.baseCurrency, locale),
                        ),
                        style = NumberSmall,
                        color = status.textSecondary,
                    )
                }
            }
        }

        items(state.accounts.size, key = { state.accounts[it].account.id }) { index ->
            val row = state.accounts[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenAccount(row.account.id) },
                        onLongClick = { pendingAccount = row.account.id },
                        onLongClickLabel = stringResource(R.string.common_delete),
                    )
                    .padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.financeRowVertical,
                    ),
            ) {
                Icon(
                    painter = painterResource(BolhIcons.resFor(row.account.icon)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = row.account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                )
                Text(
                    text = Money.format(row.balanceMinor, row.account.currencyCode, locale),
                    style = MoneyRow,
                    color = if (row.balanceMinor < 0L) {
                        status.problem
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
            }
            HorizontalDivider(
                color = status.divider,
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            )
        }

        item(key = "all-transactions") {
            Text(
                text = stringResource(R.string.finance_all_transactions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTransactions)
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            )
        }

        item(key = "spend-header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.xl,
                ),
            ) {
                Text(
                    text = stringResource(R.string.finance_month_spend),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.finance_hide_obligatory),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.hideObligatory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        status.textTertiary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::toggleObligatory)
                        .padding(Spacing.xs),
                )
            }
            ViewSwitch(current = state.view, onSelect = viewModel::setView)
        }

        item(key = "spend-body") {
            when {
                state.monthSpendMinor == 0L -> Text(
                    text = stringResource(R.string.finance_no_spend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.l,
                    ),
                )

                state.view == FinanceView.Ring -> RingBlock(state = state, locale = locale)
                state.view == FinanceView.List -> ListBlock(state = state, locale = locale)
                else -> DynamicsBlock(state = state, locale = locale)
            }
        }

        state.nextPayment?.let { payment ->
            item(key = "recurring") {
                HorizontalDivider(
                    color = status.divider,
                    modifier = Modifier.padding(horizontal = Spacing.screenPadding),
                )
                Text(
                    text = stringResource(R.string.finance_next_payment),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(
                        start = Spacing.screenPadding,
                        top = Spacing.l,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { pendingPayment = payment.id },
                            onLongClickLabel = stringResource(R.string.common_delete),
                        )
                        .padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.financeRowVertical,
                        ),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = payment.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = payment.nextDate.format(
                                DateTimeFormatter.ofPattern("d MMMM", locale),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textTertiary,
                        )
                    }
                    Text(
                        text = Money.format(payment.amountMinor, payment.currencyCode, locale),
                        style = MoneyRow,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = Spacing.m),
                    )
                    Text(
                        text = stringResource(R.string.finance_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = status.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.skipRecurring(payment.id) }
                            .padding(Spacing.xs),
                    )
                    Text(
                        text = stringResource(R.string.finance_record),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.recordRecurring(payment.id) }
                            .padding(Spacing.xs),
                    )
                }
            }
        }

        item(key = "recurring-add") {
            Text(
                text = stringResource(R.string.recurring_add),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddRecurring)
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            )
        }

        items(state.jars.size, key = { state.jars[it].jar.id }) { index ->
            val row = state.jars[index]
            HorizontalDivider(
                color = status.divider,
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.financeRowVertical,
                    ),
            ) {
                // Копилка окрашена цветом целей: связь читается без подписи (§9.3).
                Icon(
                    painter = painterResource(BolhIcons.resFor(row.goal?.icon ?: "savings")),
                    contentDescription = null,
                    tint = GoalsTeal,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = row.goal?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                )
                Text(
                    text = stringResource(
                        R.string.finance_jar_progress,
                        Money.format(row.currentMinor, row.jar.currencyCode, locale),
                        Money.format(row.jar.targetMinor, row.jar.currencyCode, locale),
                    ),
                    style = MoneyRow,
                    color = GoalsTeal,
                )
            }
        }
    }
}

/**
 * Вид расходов — редкое решение, поэтому он живёт под кнопкой, а не тремя
 * бледными словами поперёк экрана.
 */
@Composable
private fun ViewSwitch(current: FinanceView, onSelect: (FinanceView) -> Unit) {
    val status = LocalStatusColors.current
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.padding(
            start = Spacing.screenPadding,
            end = Spacing.screenPadding,
            top = Spacing.s,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.finance_view),
                style = MaterialTheme.typography.labelLarge,
                color = status.textSecondary,
            )
            Text(
                text = " · " + stringResource(viewLabel(current)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = status.surfaceRaised,
        ) {
            FinanceView.entries.forEach { view ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(viewLabel(view)),
                            color = if (view == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    },
                    onClick = {
                        onSelect(view)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RingBlock(state: FinanceUiState, locale: Locale) {
    val status = LocalStatusColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.m,
        ),
    ) {
        DonutChart(
            slices = state.spendByCategory.map { spend ->
                DonutSlice(
                    fraction = spend.share.toFloat(),
                    // Красный значит «выросло аномально», а не «следующее в списке».
                    color = sliceColor(spend, status.problem, status.textTertiary),
                )
            },
            centerText = Money.format(state.monthSpendMinor, state.baseCurrency, locale),
            trackColor = status.surfaceSunken,
        )
        Column(modifier = Modifier.padding(start = Spacing.l)) {
            state.spendByCategory.take(6).forEach { spend ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(sliceColor(spend, status.problem, status.textTertiary)),
                    )
                    Text(
                        text = categoryName(spend.category),
                        style = MaterialTheme.typography.labelMedium,
                        color = status.textSecondary,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                    Text(
                        text = "${(spend.share * 100).roundToInt()}%",
                        style = NumberSmall,
                        color = status.textTertiary,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListBlock(state: FinanceUiState, locale: Locale) {
    val status = LocalStatusColors.current
    Column(modifier = Modifier.padding(top = Spacing.s)) {
        state.spendByCategory.forEach { spend ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.financeRowVertical,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(sliceColor(spend, status.problem, status.textTertiary)),
                )
                Text(
                    text = categoryName(spend.category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                )
                spend.deltaToPrevious?.let { delta ->
                    Text(
                        text = (if (delta > 0) "+" else "") + "${(delta * 100).roundToInt()}%",
                        style = NumberSmall,
                        color = when {
                            spend.isAnomalous -> status.problem
                            delta > 0 -> status.attention
                            else -> status.textTertiary
                        },
                        modifier = Modifier.padding(end = Spacing.m),
                    )
                }
                Text(
                    text = Money.format(spend.amountMinor, state.baseCurrency, locale),
                    style = MoneyRow,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            HorizontalDivider(
                color = status.divider,
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            )
        }
    }
}

@Composable
private fun DynamicsBlock(state: FinanceUiState, locale: Locale) {
    val status = LocalStatusColors.current
    val maxAmount = state.dynamics.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.l,
        ),
    ) {
        state.dynamics.forEach { month ->
            val fraction = month.amountMinor.toFloat() / maxAmount
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = Money.formatPlain(month.amountMinor, locale = locale),
                    style = NumberSmall,
                    color = status.textTertiary,
                )
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .width(22.dp)
                        .height((6 + 90 * fraction).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (month == state.dynamics.last()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                status.surfaceSunken
                            },
                        ),
                )
                Text(
                    text = month.month.month
                        .getDisplayName(TextStyle.SHORT, locale)
                        .take(3),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun categoryName(category: Category?): String {
    if (category == null) return stringResource(R.string.finance_uncategorised)
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}

/** Красный значит «выросло аномально», а не «следующее по списку» (§6.8). */
private fun sliceColor(spend: CategorySpend, problem: Color, fallback: Color): Color = when {
    spend.isAnomalous -> problem
    spend.category != null -> Color(spend.category.colorArgb)
    else -> fallback
}

private fun viewLabel(view: FinanceView): Int = when (view) {
    FinanceView.Ring -> R.string.finance_view_ring
    FinanceView.List -> R.string.finance_view_list
    FinanceView.Dynamics -> R.string.finance_view_dynamics
}
