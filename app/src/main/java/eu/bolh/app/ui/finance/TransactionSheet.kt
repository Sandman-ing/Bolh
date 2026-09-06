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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.TransactionType
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyLarge
import eu.bolh.app.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Быстрый путь: тип, сумма, категория, счёт, «Записать» (§6.9).
 * Дата, заметка и привязка к цели спрятаны под одну строку — они нужны редко,
 * а ежедневное действие должно занимать секунды.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
    /** Счёт, с которого открыли шторку: изнутри счёта выбирать его заново — лишний шаг. */
    accountId: String? = null,
    viewModel: TransactionEditorViewModel = viewModel(factory = TransactionEditorViewModel.Factory),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val locale = rememberLocale()

    LaunchedEffect(options.accounts, accountId) {
        if (draft.accountId == null) {
            val preselected = accountId ?: options.accounts.firstOrNull()?.id
            preselected?.let { viewModel.setAccount(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = status.surfaceRaised,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            ) {
                TransactionType.entries.forEach { type ->
                    SheetChip(
                        text = stringResource(typeLabel(type)),
                        selected = draft.type == type,
                        onClick = { viewModel.setType(type) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text(stringResource(R.string.tx_amount)) },
                singleLine = true,
                textStyle = MoneyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
            )

            if (draft.type != TransactionType.Transfer) {
                Text(
                    text = stringResource(R.string.tx_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(start = Spacing.screenPadding, bottom = Spacing.xs),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
                ) {
                    items(options.categoriesFor(draft.type), key = { it.id }) { category ->
                        SheetChip(
                            text = categoryLabel(category),
                            selected = draft.categoryId == category.id,
                            onClick = {
                                viewModel.setCategory(
                                    if (draft.categoryId == category.id) null else category.id,
                                )
                            },
                            icon = category.icon,
                            accent = Color(category.colorArgb),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.tx_account),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    top = Spacing.m,
                    bottom = Spacing.xs,
                ),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
            ) {
                items(options.accounts, key = { it.id }) { account ->
                    SheetChip(
                        text = account.name,
                        selected = draft.accountId == account.id,
                        onClick = { viewModel.setAccount(account.id) },
                        icon = account.icon,
                    )
                }
            }

            if (draft.type == TransactionType.Transfer) {
                Text(
                    text = stringResource(R.string.tx_account_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(
                        start = Spacing.screenPadding,
                        top = Spacing.m,
                        bottom = Spacing.xs,
                    ),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
                ) {
                    items(
                        options.accounts.filter { it.id != draft.accountId },
                        key = { it.id },
                    ) { account ->
                        SheetChip(
                            text = account.name,
                            selected = draft.toAccountId == account.id,
                            onClick = { viewModel.setToAccount(account.id) },
                            icon = account.icon,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.tx_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::toggleExpanded)
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            )

            if (draft.expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    viewModel.setDate(java.time.LocalDate.of(year, month + 1, day))
                                },
                                draft.date.year,
                                draft.date.monthValue - 1,
                                draft.date.dayOfMonth,
                            ).show()
                        }
                        .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
                ) {
                    Text(
                        text = stringResource(R.string.tx_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = draft.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = draft.note,
                    onValueChange = viewModel::setNote,
                    label = { Text(stringResource(R.string.tx_note)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
                )

                if (options.goals.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tx_goal),
                        style = MaterialTheme.typography.labelMedium,
                        color = status.textTertiary,
                        modifier = Modifier.padding(
                            start = Spacing.screenPadding,
                            top = Spacing.s,
                            bottom = Spacing.xs,
                        ),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
                    ) {
                        items(options.goals, key = { it.id }) { goal ->
                            SheetChip(
                                text = goal.name,
                                selected = draft.goalId == goal.id,
                                onClick = {
                                    viewModel.setGoal(if (draft.goalId == goal.id) null else goal.id)
                                },
                                icon = goal.icon,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (options.accounts.isEmpty()) onAddAccount() else viewModel.record(onDismiss)
                },
                // Записать некуда, пока нет ни одного счёта — тогда кнопка ведёт заводить счёт.
                enabled = options.accounts.isEmpty() || draft.canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Ink,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
            ) {
                Text(
                    stringResource(
                        if (options.accounts.isEmpty()) {
                            R.string.finance_empty_action
                        } else {
                            R.string.tx_record
                        },
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetChip(
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
        modifier = Modifier.clip(RoundedCornerShape(percent = 50)),
    )
}

@Composable
private fun categoryLabel(category: Category): String {
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}

private fun typeLabel(type: TransactionType): Int = when (type) {
    TransactionType.Expense -> R.string.tx_expense
    TransactionType.Income -> R.string.tx_income
    TransactionType.Transfer -> R.string.tx_transfer
}
