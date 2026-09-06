package eu.bolh.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.BolhApplication
import eu.bolh.app.R
import eu.bolh.app.data.repository.FinanceRepository
import eu.bolh.app.domain.model.Category
import eu.bolh.app.domain.model.CategoryKind
import eu.bolh.app.domain.templates.SystemCategories
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val finance: FinanceRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = finance.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun create(name: String, icon: String, colorArgb: Int, kind: CategoryKind) {
        if (name.isBlank()) return
        viewModelScope.launch { finance.createCategory(name, icon, colorArgb, kind) }
    }

    fun update(id: String, name: String, icon: String, colorArgb: Int) {
        viewModelScope.launch { finance.updateCategory(id, name, icon, colorArgb) }
    }

    fun delete(id: String) {
        viewModelScope.launch { finance.deleteCategory(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                CategoriesViewModel(app.container.financeRepository)
            }
        }
    }
}

/**
 * Категории: сетка кружков своего цвета, расходы и доходы двумя вкладками.
 *
 * Цвет здесь не украшение — это то, чем категория обозначена в кольце расходов,
 * поэтому выбирать его должен человек, а не приложение (§6.8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current

    var kind by remember { mutableStateOf(CategoryKind.Expense) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Category?>(null) }

    val visible = categories.filter { it.kind == kind }

    pending?.let { category ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_category, categoryTitle(category)),
            onConfirm = {
                viewModel.delete(category.id)
                editing = null
            },
            onDismiss = { pending = null },
        )
    }

    if (creating || editing != null) {
        CategorySheet(
            category = editing,
            kind = kind,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { name, icon, color ->
                val current = editing
                if (current == null) {
                    viewModel.create(name, icon, color, kind)
                } else {
                    viewModel.update(current.id, name, icon, color)
                }
                creating = false
                editing = null
            },
            onDelete = { pending = editing },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.categories_title),
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
                KindTab(
                    text = stringResource(R.string.finance_flow_expense),
                    selected = kind == CategoryKind.Expense,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { kind = CategoryKind.Expense },
                )
                KindTab(
                    text = stringResource(R.string.finance_flow_income),
                    selected = kind == CategoryKind.Income,
                    accent = status.success,
                    onClick = { kind = CategoryKind.Income },
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = Spacing.m,
                    end = Spacing.m,
                    top = Spacing.s,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                items(visible.size, key = { visible[it].id }) { index ->
                    val category = visible[index]
                    CategoryTile(
                        name = categoryTitle(category),
                        icon = BolhIcons.resFor(category.icon),
                        color = Color(category.colorArgb),
                        onClick = { editing = category },
                    )
                }
                item(key = "new") {
                    CategoryTile(
                        name = stringResource(R.string.category_new),
                        icon = R.drawable.ic_plus,
                        color = status.textTertiary,
                        onClick = { creating = true },
                    )
                }
            }
        }
    }
}

/** Кружок категории: цвет — её собственный, значок внутри белый. */
@Composable
private fun CategoryTile(
    name: String,
    icon: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.block))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.s),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun KindTab(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.chip)

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Ink else status.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent else status.surfaceSunken)
            .border(
                width = 1.dp,
                color = if (selected) accent else status.strokeInner,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}

/** Форма категории: название, значок, цвет. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    category: Category?,
    kind: CategoryKind,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val status = LocalStatusColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fallbackName = category?.let { categoryTitle(it) }.orEmpty()

    var name by remember(category) { mutableStateOf(fallbackName) }
    var icon by remember(category) { mutableStateOf(category?.icon ?: BolhIcons.finance.first()) }
    var color by remember(category) {
        mutableStateOf(category?.colorArgb ?: CategoryPalette.first().toInt())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = status.surfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    bottom = Spacing.xxl,
                ),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.category_icon),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.s),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                BolhIcons.finance.forEach { key ->
                    val chosen = key == icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (chosen) Color(color) else status.surfaceSunken)
                            .border(
                                width = 1.dp,
                                color = if (chosen) Color(color) else status.strokeInner,
                                shape = CircleShape,
                            )
                            .clickable { icon = key },
                    ) {
                        Icon(
                            painter = painterResource(BolhIcons.resFor(key)),
                            contentDescription = null,
                            tint = if (chosen) Ink else status.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.category_color),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.s),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                CategoryPalette.forEach { swatch ->
                    val value = swatch.toInt()
                    val chosen = value == color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = if (chosen) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    status.strokeInner
                                },
                                shape = CircleShape,
                            )
                            .clickable { color = value },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(top = Spacing.xl),
            ) {
                PillButton(
                    text = stringResource(R.string.account_save),
                    onClick = { onSave(name, icon, color) },
                    modifier = Modifier.weight(1f),
                )
                // Готовую категорию удалять нельзя: на ней держатся уже
                // записанные операции и признак обязательного платежа.
                if (category != null && !category.isSystem) {
                    PillButton(
                        text = stringResource(R.string.common_delete),
                        onClick = onDelete,
                        color = status.problem,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (category?.isSystem == true) {
                Text(
                    text = stringResource(R.string.category_system_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
        }
    }
}

/**
 * Цвета на выбор. Статусные (§9.3) сюда не входят: красный, зелёный и оранжевый
 * значат «проблема», «сделано» и «внимание» — категория не должна их занимать.
 */
private val CategoryPalette: List<Long> = listOf(
    0xFF63C2A0, 0xFFD9A05B, 0xFF6E9BD8, 0xFF9B87E0,
    0xFFD87C8A, 0xFFC584C4, 0xFF5BB8C9, 0xFF8895A8,
    0xFF7FC8EC, 0xFFC2926B, 0xFF7E8FD4, 0xFF79838F,
    0xFF9CC77A, 0xFFE0B8D0, 0xFF6FA3A0, 0xFFB0A46E,
)

@Composable
private fun categoryTitle(category: Category): String {
    val res = SystemCategories.nameResFor(category.nameKey)
    return if (res != null) stringResource(res) else category.name
}
