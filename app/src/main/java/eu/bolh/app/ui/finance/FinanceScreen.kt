package eu.bolh.app.ui.finance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ripple
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.SectionCard
import eu.bolh.app.ui.common.ToggleChip
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.EmptyState
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.finance.components.DonutChart
import eu.bolh.app.ui.finance.components.DonutSlice
import eu.bolh.app.ui.theme.GoalsTeal
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.MoneyHero
import eu.bolh.app.ui.theme.MoneyLarge
import eu.bolh.app.ui.theme.MoneyRow
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Экран финансов — приборная панель (§9.2): строки-выписка, моноширинные цифры,
 * тонкие разделители. Читается как банковская выписка, что для денег уместно.
 */
/**
 * Подвкладки финансов.
 *
 * Обзор отвечает на «сколько и куда», всё остальное — отдельные занятия,
 * которые случаются раз в месяц. На одном экране они спорили за внимание
 * с главным числом.
 */
private enum class FinanceTab { Overview, Payments, Jars }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FinanceScreen(
    onAddAccount: () -> Unit,
    onAddTransaction: () -> Unit,
    onAddRecurring: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenRecurring: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinanceViewModel = viewModel(factory = FinanceViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    var pendingAccount by remember { mutableStateOf<String?>(null) }
    var pendingJar by remember { mutableStateOf<String?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(FinanceTab.Overview) }
    var pendingPayment by remember { mutableStateOf<String?>(null) }

    pendingAccount?.let { id ->
        val account = state.accounts.firstOrNull { it.account.id == id }?.account
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete, account?.name.orEmpty()),
            onConfirm = { viewModel.deleteAccount(id) },
            onDismiss = { pendingAccount = null },
        )
    }

    pendingJar?.let { id ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_jar),
            onConfirm = { viewModel.deleteJar(id) },
            onDismiss = { pendingJar = null },
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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(
                horizontal = Spacing.screenPadding,
                vertical = Spacing.s,
            ),
        ) {
            SubTab(
                text = stringResource(R.string.finance_tab_overview),
                selected = tab == FinanceTab.Overview,
                onClick = { tab = FinanceTab.Overview },
                modifier = Modifier.weight(1f),
            )
            SubTab(
                text = stringResource(R.string.finance_tab_payments),
                selected = tab == FinanceTab.Payments,
                onClick = { tab = FinanceTab.Payments },
                modifier = Modifier.weight(1f),
            )
            SubTab(
                text = stringResource(R.string.finance_tab_jars),
                selected = tab == FinanceTab.Jars,
                onClick = { tab = FinanceTab.Jars },
                modifier = Modifier.weight(1f),
            )
        }

    // Подвкладка меняется со сдвигом в сторону нажатой: резкая подмена
    // содержимого читается как перезагрузка экрана.
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val shift = if (forward) 1 else -1
            (
                slideInHorizontally(tween(TAB_MOVE_MS)) { shift * it / 8 } +
                    fadeIn(tween(TAB_FADE_MS))
                ) togetherWith (
                slideOutHorizontally(tween(TAB_MOVE_MS)) { -shift * it / 8 } +
                    fadeOut(tween(TAB_FADE_MS))
                )
        },
        label = "finance-tab",
    ) { shown ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Запас снизу под плавающую кнопку: иначе она накрывает последнюю строку.
        contentPadding = PaddingValues(bottom = Spacing.fabClearance),
    ) {
        if (shown == FinanceTab.Overview) {
        item(key = "total") {
            BolhCard(
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.l,
                ),
            ) {
                // Шапка отвечает на «сколько у меня», и счёт выбирается здесь:
                // иначе за остатком одного счёта надо уходить на другой экран.
                Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.chip))
                            .clickable { accountMenu = true }
                            .padding(vertical = Spacing.xs),
                    ) {
                        Text(
                            text = state.selectedAccount?.account?.name
                                ?: stringResource(R.string.finance_all_accounts),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_down),
                            contentDescription = stringResource(R.string.finance_total),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = Spacing.xs)
                                .size(14.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = accountMenu,
                        onDismissRequest = { accountMenu = false },
                        containerColor = status.surfaceRaised,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.finance_all_accounts)) },
                            onClick = {
                                viewModel.selectAccount(null)
                                accountMenu = false
                            },
                        )
                        state.accounts.forEach { row ->
                            DropdownMenuItem(
                                text = { Text(row.account.name) },
                                onClick = {
                                    viewModel.selectAccount(row.account.id)
                                    accountMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // Вид и фильтр — редкие решения, поэтому они под одной
                // маленькой кнопкой, а не занимают собой отдельный блок.
                Box {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = stringResource(R.string.finance_options),
                        tint = status.textSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { optionsMenu = true }
                            .padding(Spacing.xs)
                            .size(20.dp),
                    )
                    DropdownMenu(
                        expanded = optionsMenu,
                        onDismissRequest = { optionsMenu = false },
                        containerColor = status.surfaceRaised,
                    ) {
                        FinanceView.entries.forEach { view ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(viewLabel(view)),
                                        color = if (view == state.view) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                    )
                                },
                                onClick = {
                                    viewModel.setView(view)
                                    optionsMenu = false
                                },
                            )
                        }
                        if (state.flow == MoneyFlow.Expense) {
                            HorizontalDivider(color = status.divider)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(
                                            R.string.finance_hide_obligatory_clear,
                                        ),
                                        color = if (state.hideObligatory) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                    )
                                },
                                onClick = {
                                    viewModel.toggleObligatory()
                                    optionsMenu = false
                                },
                            )
                        }
                    }
                }
                }
                // Остаток — в центре, движение денег — кольцом вокруг него.
                // Так одна картинка отвечает сразу на «сколько есть» и «куда уходит».
                val flowAccent = if (state.flow == MoneyFlow.Expense) {
                    MaterialTheme.colorScheme.primary
                } else {
                    status.success
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.m),
                ) {
                    DonutChart(
                        slices = state.spendByCategory.map { spend ->
                            DonutSlice(
                                fraction = spend.share.toFloat(),
                                color = sliceColor(spend, status.problem, status.textTertiary),
                            )
                        },
                        centerText = Money.format(
                            state.headlineMinor,
                            state.analysedCurrency,
                            locale,
                        ),
                        centerCaption = flowCaption(state, locale),
                        captionColor = flowAccent,
                        trackColor = status.surfaceSunken,
                    )
                }

                when (state.view) {
                    FinanceView.Ring -> RingLegend(state = state, locale = locale)
                    FinanceView.List -> ListBlock(state = state, locale = locale)
                    FinanceView.Dynamics -> DynamicsBlock(state = state, locale = locale)
                }
                // Без этой строки человек считает своими деньги, уже обещанные цели.
                if (state.reservedMinor > 0L) {
                    Text(
                        text = stringResource(
                            R.string.finance_reserved,
                            Money.format(state.reservedMinor, state.analysedCurrency, locale),
                        ),
                        style = NumberSmall,
                        color = GoalsTeal,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = stringResource(
                            R.string.finance_available,
                            Money.format(state.availableMinor, state.analysedCurrency, locale),
                        ),
                        style = NumberSmall,
                        color = status.textSecondary,
                    )
                }
            }
        }

        item(key = "what") {
            SectionCard(
                title = stringResource(R.string.finance_group_what),
                titleColor = status.textTertiary,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FlowTab(
                        text = stringResource(R.string.finance_flow_expense),
                        selected = state.flow == MoneyFlow.Expense,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.setFlow(MoneyFlow.Expense) },
                        modifier = Modifier.weight(1f),
                    )
                    FlowTab(
                        text = stringResource(R.string.finance_flow_income),
                        selected = state.flow == MoneyFlow.Income,
                        accent = status.success,
                        onClick = { viewModel.setFlow(MoneyFlow.Income) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(top = Spacing.s),
                ) {
                    FinancePeriod.entries.forEach { period ->
                        ToggleChip(
                            text = stringResource(periodLabel(period)),
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Валюты нет смысла предлагать, пока она одна: строка чипов с
                // единственным выбором — это не выбор, а шум.
                AnimatedVisibility(
                    visible = state.currencies.size > 1,
                    enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
                    exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.finance_currency_scope),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textTertiary,
                            modifier = Modifier.padding(top = Spacing.m),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = Spacing.xs),
                        ) {
                            state.currencies.forEach { code ->
                                ToggleChip(
                                    text = code,
                                    selected = state.analysedCurrency == code,
                                    onClick = { viewModel.selectCurrency(code) },
                                )
                            }
                        }
                    }
                }
            }
        }

        items(state.accounts.size, key = { state.accounts[it].account.id }) { index ->
            val row = state.accounts[index]
            // Счёт — вход внутрь, к своим расходам и доходам, поэтому у него
            // стрелка: без неё нажатие остаётся тайной.
            BolhCard(
                modifier = Modifier
                    .padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.xs,
                    )
                    .tappable(
                        onClick = { onOpenAccount(row.account.id) },
                        onLongClick = { pendingAccount = row.account.id },
                        longClickLabel = stringResource(R.string.common_delete),
                    ),
                contentPadding = PaddingValues(
                    horizontal = Spacing.l,
                    vertical = Spacing.m,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        // Валюта берётся у самого счёта: Wise в евро и
                        // наличные в драмах стоят в одном списке рядом.
                        text = Money.format(row.balanceMinor, row.account.currencyCode, locale),
                        style = MoneyRow,
                        color = if (row.balanceMinor < 0L) {
                            status.problem
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = status.textTertiary,
                        modifier = Modifier
                            .padding(start = Spacing.xs)
                            .size(18.dp),
                    )
                }
            }
        }

        item(key = "shortcuts") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier
                    .padding(
                        start = Spacing.screenPadding,
                        end = Spacing.screenPadding,
                        top = Spacing.m,
                        bottom = Spacing.s,
                    )
                    .height(IntrinsicSize.Min),
            ) {
                ShortcutTile(
                    title = stringResource(R.string.finance_all_transactions),
                    hint = stringResource(R.string.finance_tile_transactions_hint),
                    icon = R.drawable.ic_receipt_long,
                    onClick = onOpenTransactions,
                    modifier = Modifier.weight(1f),
                )
                ShortcutTile(
                    title = stringResource(R.string.categories_title),
                    hint = stringResource(R.string.finance_tile_categories_hint),
                    icon = R.drawable.ic_shopping_cart,
                    onClick = onOpenCategories,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Кнопки разложены по смыслу: сначала «какие данные», отдельно
        // «в каком виде». В одной куче они читались как один набор.

        }

        if (shown == FinanceTab.Payments) {

        if (state.payments.isEmpty()) {
            item(key = "no-payments") {
                BolhCard(
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.s,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.finance_no_payments),
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.textTertiary,
                    )
                }
            }
        }

        items(state.payments.size, key = { state.payments[it].id }) { index ->
            val payment = state.payments[index]
            run {
                // Ближайший платёж обведён цветом раздела: из списка сразу
                // видно, за какой из них браться первым.
                BolhCard(
                    accent = if (payment.id == state.nextPayment?.id) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        null
                    },
                    modifier = Modifier
                        .padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.xs,
                        )
                        .tappable(
                            onClick = { onOpenRecurring(payment.id) },
                            onLongClick = { pendingPayment = payment.id },
                            longClickLabel = stringResource(R.string.common_delete),
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = payment.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = payment.nextDate.format(
                                    DateTimeFormatter.ofPattern(stringResource(R.string.date_day_month), locale),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = status.textTertiary,
                            )
                        }
                        Text(
                            text = Money.format(payment.amountMinor, payment.currencyCode, locale),
                            style = MoneyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.padding(top = Spacing.m),
                    ) {
                        PillButton(
                            text = stringResource(R.string.finance_record),
                            onClick = { viewModel.recordRecurring(payment.id) },
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = stringResource(R.string.finance_skip),
                            onClick = { viewModel.skipRecurring(payment.id) },
                            color = status.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item(key = "recurring-add") {
            PillButton(
                text = stringResource(R.string.recurring_add),
                onClick = onAddRecurring,
                icon = R.drawable.ic_plus,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            )
        }

        }

        if (shown == FinanceTab.Jars) {

        if (state.jars.isEmpty()) {
            item(key = "no-jars") {
                BolhCard(
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.s,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.finance_no_jars),
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.textTertiary,
                    )
                }
            }
        }

        items(state.jars.size, key = { state.jars[it].jar.id }) { index ->
            val row = state.jars[index]
            BolhCard(
                modifier = Modifier
                    .padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.xs,
                    )
                    // Копилка без своей цели остаётся строкой без имени —
                    // и её должно быть чем убрать.
                    .tappable(
                        onClick = {},
                        onLongClick = { pendingJar = row.jar.id },
                        longClickLabel = stringResource(R.string.common_delete),
                    ),
                contentPadding = PaddingValues(
                    horizontal = Spacing.l,
                    vertical = Spacing.m,
                ),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
    }
    }
    }
}

/** Длительности переключения подвкладки. */
private const val TAB_MOVE_MS = 220
private const val TAB_FADE_MS = 150

/**
 * Крупная плитка перехода: значок, название, строка о том, что внутри.
 *
 * Слово посреди прямоугольника не говорит, куда ведёт; плитка говорит,
 * и держит тот же ритм, что карточки выше.
 */
@Composable
private fun ShortcutTile(
    title: String,
    hint: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current

    BolhCard(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(Spacing.l),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.block))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Spacing.m),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Подвкладка раздела: выбранная залита, остальные обведены. */
@Composable
private fun SubTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.chip)
    val press = rememberPressSource()

    val fill by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            status.surfaceRaised
        },
        animationSpec = Motion.color,
        label = "subtab-fill",
    )
    val label by animateColorAsState(
        targetValue = if (selected) Ink else status.textSecondary,
        animationSpec = Motion.color,
        label = "subtab-label",
    )

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = label,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(fill)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else status.stroke,
                shape = shape,
            )
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.m),
    )
}

/** Вкладка блока: выбранная залита своим цветом, остальная — обведена. */
@Composable
private fun FlowTab(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.chip)
    val press = rememberPressSource()

    val fill by animateColorAsState(
        targetValue = if (selected) accent else status.surfaceSunken,
        animationSpec = Motion.color,
        label = "flow-fill",
    )
    val label by animateColorAsState(
        targetValue = if (selected) Ink else status.textSecondary,
        animationSpec = Motion.color,
        label = "flow-label",
    )

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = label,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(fill)
            .border(
                width = 1.dp,
                color = if (selected) accent else status.strokeInner,
                shape = shape,
            )
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}

/**
 * Вид расходов — редкое решение, поэтому он живёт под кнопкой, а не тремя
 * бледными словами поперёк экрана.
 */
@Composable
private fun ViewSwitch(current: FinanceView, onSelect: (FinanceView) -> Unit) {
    val status = LocalStatusColors.current
    var open by remember { mutableStateOf(false) }

    Box {
        val shape = RoundedCornerShape(Radius.chip)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(status.surfaceSunken)
                .border(1.dp, status.strokeInner, shape)
                .clickable { open = true }
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        ) {
            Text(
                text = stringResource(viewLabel(current)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = stringResource(R.string.finance_view),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Spacing.xs)
                    .size(14.dp),
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

/**
 * Легенда кольца.
 *
 * Под кольцом, а не сбоку: в колонке шириной в треть экрана
 * «Кафе и доставка 68%» разваливается по буквам.
 */
@Composable
private fun RingLegend(state: FinanceUiState, locale: Locale) {
    val status = LocalStatusColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.spendByCategory.isEmpty()) {
            Text(
                text = if (state.flow == MoneyFlow.Expense) {
                    stringResource(R.string.finance_no_spend)
                } else {
                    stringResource(R.string.finance_no_income)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = status.textTertiary,
            )
        }
        state.spendByCategory.take(6).forEach { spend ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(sliceColor(spend, status.problem, status.textTertiary)),
                )
                Text(
                    text = categoryName(spend.category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                )
                Text(
                    text = Money.format(spend.amountMinor, state.analysedCurrency, locale),
                    style = NumberSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "${(spend.share * 100).roundToInt()}%",
                    style = NumberSmall,
                    color = status.textTertiary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}

@Composable
private fun ListBlock(state: FinanceUiState, locale: Locale) {
    val status = LocalStatusColors.current
    Column {
        state.spendByCategory.forEach { spend ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.s),
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
                    text = Money.format(spend.amountMinor, state.analysedCurrency, locale),
                    style = MoneyRow,
                    // Перебор лимита — статус, а не оттенок: цвет перебивает
                    // цвет категории, потому что «вышел за план» важнее (§9.3).
                    color = when {
                        spend.overPlan -> status.problem
                        spend.nearPlan -> status.attention
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                )
            }
            // Лимит показывается только там, где человек его поставил (§6.7).
            spend.plannedMinor?.let { planned ->
                Text(
                    text = stringResource(
                        R.string.category_plan_of,
                        Money.format(spend.amountMinor, state.analysedCurrency, locale),
                        Money.format(planned, state.analysedCurrency, locale),
                    ),
                    style = NumberSmall,
                    color = when {
                        spend.overPlan -> status.problem
                        spend.nearPlan -> status.attention
                        else -> status.textTertiary
                    },
                    modifier = Modifier.padding(start = Spacing.xl, bottom = Spacing.s),
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

/** Что сейчас нарисовано кольцом: вид движения и его сумма за период. */
@Composable
private fun flowCaption(state: FinanceUiState, locale: Locale): String {
    val label = if (state.flow == MoneyFlow.Expense) {
        stringResource(R.string.finance_flow_expense)
    } else {
        stringResource(R.string.finance_flow_income)
    }
    val amount = Money.format(state.periodTotalMinor, state.analysedCurrency, locale)
    return "$label · $amount"
}

private fun periodLabel(period: FinancePeriod): Int = when (period) {
    FinancePeriod.Day -> R.string.finance_period_day
    FinancePeriod.Week -> R.string.finance_period_week
    FinancePeriod.Month -> R.string.finance_period_month
    FinancePeriod.Year -> R.string.finance_period_year
}

private fun viewLabel(view: FinanceView): Int = when (view) {
    FinanceView.Ring -> R.string.finance_view_ring
    FinanceView.List -> R.string.finance_view_list
    FinanceView.Dynamics -> R.string.finance_view_dynamics
}
