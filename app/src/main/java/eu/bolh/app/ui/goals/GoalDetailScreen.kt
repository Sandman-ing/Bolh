package eu.bolh.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.GoalStatus
import androidx.compose.animation.animateColorAsState
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.MetricChip
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.RenameDialog
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.completionPop
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.CompletionPulse
import eu.bolh.app.ui.common.ConfirmDeleteDialog
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.common.Money
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.HabitsViolet
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Карточка цели изнутри. Прогноз и цена живут только здесь: неприятные выводы
 * показываются там, куда человек зашёл сам, и никогда не выносятся на главный
 * экран (§3.4).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalDetailViewModel = viewModel(factory = GoalDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val newStage by viewModel.newStage.collectAsStateWithLifecycle()
    val suggested by viewModel.suggestedDeadline.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val goal = state.goal
    var jarTarget by remember { mutableStateOf("") }
    var jarAmount by remember { mutableStateOf("") }
    var editingJar by remember { mutableStateOf(false) }
    var addingBudget by remember { mutableStateOf(false) }
    var pendingStage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var habitPicker by remember { mutableStateOf(false) }
    var pendingGoal by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var pendingTask by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (pendingGoal && goal != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_goal, goal.name),
            onConfirm = {
                viewModel.delete()
                onBack()
            },
            onDismiss = { pendingGoal = false },
        )
    }

    pendingStage?.let { (id, name) ->
        RenameDialog(
            initial = name,
            onRename = { viewModel.renameStage(id, it) },
            onDelete = { viewModel.deleteStage(id) },
            onDismiss = { pendingStage = null },
        )
    }
    pendingTask?.let { (id, name) ->
        RenameDialog(
            initial = name,
            onRename = { viewModel.renameTask(id, it) },
            onDelete = { viewModel.deleteTask(id) },
            onDismiss = { pendingTask = null },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = goal?.name.orEmpty(),
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
                    goal?.let {
                        TextButton(onClick = { onEdit(it.id) }) {
                            Text(stringResource(R.string.goal_edit_title))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        if (goal == null) return@Scaffold

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            BolhCard(
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.goal_period,
                        monthYear(goal.startDate, locale),
                        goal.deadline?.let { monthYear(it, locale) }
                            ?: stringResource(R.string.goal_no_deadline),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isOverdue) status.problem else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (state.linkedHabits.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        state.linkedHabits.take(5).forEach { habit ->
                            Icon(
                                painter = painterResource(BolhIcons.resFor(habit.icon)),
                                contentDescription = null,
                                tint = HabitsViolet,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            state.progress?.let { progress ->
                Box(
                    modifier = Modifier
                        .padding(vertical = Spacing.m)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(status.surfaceSunken),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.goal_progress,
                        (progress * 100).roundToInt(),
                        state.stages.count { it.stage.isDone },
                    pluralStringResource(
                        R.plurals.stages_of,
                        state.stages.size,
                        state.stages.size,
                    ),
                    ),
                    style = NumberSmall,
                    color = status.textSecondary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(top = Spacing.m),
            ) {
                MetricChip(
                    text = state.tempo?.let {
                        stringResource(R.string.goal_tempo, (it * 100).roundToInt())
                    } ?: stringResource(R.string.goal_tempo_unknown),
                    color = when {
                        state.tempo == null -> status.textTertiary
                        state.tempo!! >= 0.6 -> status.success
                        state.tempo!! >= 0.3 -> status.attention
                        else -> status.problem
                    },
                )
            }
            }

            // Просроченная цель: новый срок предлагается сразу поправленным (§4.6).
            // Никакой красной метки «провалено» — промах это данные, а не приговор.
            suggested?.let { date ->
                InfoBlock(
                    title = stringResource(R.string.goal_reschedule_title),
                    titleColor = status.attention,
                ) {
                    Text(
                        text = stringResource(R.string.goal_reschedule_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    OutlinedButton(
                        onClick = { viewModel.reschedule(date) },
                        modifier = Modifier.padding(top = Spacing.m),
                    ) {
                        Text(stringResource(R.string.goal_take_deadline, monthYear(date, locale)))
                    }
                }
            }

            state.forecast?.let { forecast ->
                val date = monthYear(forecast.date, locale)
                val months = forecast.monthsVsDeadline
                InfoBlock(
                    title = stringResource(R.string.goal_forecast_title),
                    titleColor = status.textTertiary,
                ) {
                    Text(
                        text = when {
                            months == null -> stringResource(R.string.goal_forecast_plain, date)
                            months > 0L -> stringResource(
                                R.string.goal_forecast_later,
                                date,
                                pluralStringResource(
                                    R.plurals.goal_months,
                                    months.toInt(),
                                    months.toInt(),
                                ),
                            )
                            months < 0L -> stringResource(
                                R.string.goal_forecast_earlier,
                                date,
                                pluralStringResource(
                                    R.plurals.goal_months,
                                    abs(months).toInt(),
                                    abs(months).toInt(),
                                ),
                            )
                            else -> stringResource(R.string.goal_forecast_on_time, date)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            months == null || months == 0L -> MaterialTheme.colorScheme.onBackground
                            months > 0L -> status.attention
                            else -> status.success
                        },
                    )
                }
            }

            state.minutesPerWeek?.let { minutes ->
                InfoBlock(
                    title = stringResource(R.string.goal_cost_title),
                    titleColor = status.textTertiary,
                ) {
                    Text(
                        text = stringResource(
                            R.string.goal_cost_time,
                            stringResource(
                                R.string.goal_cost_time_hours,
                                minutes / 60,
                                minutes % 60,
                            ),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (state.habitsWithoutEstimate > 0) {
                        Text(
                            text = stringResource(
                                R.string.goal_cost_no_estimate,
                                state.habitsWithoutEstimate,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textTertiary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }

            // Привычки цели правятся здесь же: раньше за привязкой надо было
            // идти в привычку и выбирать цель оттуда — задом наперёд.
            InfoBlock(
                title = stringResource(R.string.goal_habits_title),
                titleColor = HabitsViolet,
            ) {
                if (state.linkedHabits.isEmpty()) {
                    Text(
                        text = stringResource(R.string.goal_habits_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = status.textTertiary,
                    )
                }
                state.linkedHabits.forEach { habit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                    ) {
                        Icon(
                            painter = painterResource(BolhIcons.resFor(habit.icon)),
                            contentDescription = null,
                            tint = HabitsViolet,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Spacing.m),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.common_delete),
                            tint = status.textTertiary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { viewModel.unlinkHabit(habit.id) }
                                .padding(Spacing.xs)
                                .size(16.dp),
                        )
                    }
                }
                Box(modifier = Modifier.padding(top = Spacing.m)) {
                    PillButton(
                        text = stringResource(R.string.goal_habits_add),
                        onClick = { habitPicker = true },
                        icon = R.drawable.ic_plus,
                        color = HabitsViolet,
                    )
                    DropdownMenu(
                        expanded = habitPicker,
                        onDismissRequest = { habitPicker = false },
                        containerColor = status.surfaceRaised,
                    ) {
                        if (state.freeHabits.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.goal_habits_free)) },
                                onClick = { habitPicker = false },
                            )
                        }
                        state.freeHabits.forEach { habit ->
                            DropdownMenuItem(
                                text = { Text(habit.name) },
                                onClick = {
                                    viewModel.linkHabit(habit.id)
                                    habitPicker = false
                                },
                            )
                        }
                    }
                }
            }

            // Бюджет — единственное место, где цель и деньги встречаются напрямую.
            // Пока его не позвали, он не занимает экран: по умолчанию всё выключено (§3.1).
            val jar = state.jar
            if (jar == null && !addingBudget) {
                PillButton(
                    text = stringResource(R.string.goal_budget_add),
                    onClick = { addingBudget = true },
                    icon = R.drawable.ic_plus,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.m,
                    ),
                )
            } else {
            InfoBlock(
                title = stringResource(R.string.goal_jar_title),
                titleColor = status.textTertiary,
            ) {
                if (jar == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = jarTarget,
                            onValueChange = { jarTarget = it },
                            label = { Text(stringResource(R.string.goal_jar_target_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                viewModel.createJar(parseMinor(jarTarget))
                                jarTarget = ""
                                addingBudget = false
                            },
                        ) {
                            Text(stringResource(R.string.goal_jar_create))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(
                            R.string.finance_jar_progress,
                            Money.format(state.jarCurrentMinor, state.baseCurrency, locale),
                            Money.format(jar.targetMinor, state.baseCurrency, locale),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = Spacing.s)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(status.surfaceSunken),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    if (jar.targetMinor > 0L) {
                                        (state.jarCurrentMinor.toFloat() / jar.targetMinor)
                                            .coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    },
                                )
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.s),
                    ) {
                        OutlinedTextField(
                            value = jarAmount,
                            onValueChange = { jarAmount = it },
                            label = { Text(stringResource(R.string.goal_jar_add_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                viewModel.addContribution(parseMinor(jarAmount))
                                jarAmount = ""
                            },
                        ) {
                            Text(stringResource(R.string.goal_jar_add))
                        }
                    }

                    // Сумма цели меняется, пополнение снимается, копилка удаляется:
                    // ошибиться на вводе денег слишком легко, чтобы это было навсегда.
                    if (editingJar) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = Spacing.s),
                        ) {
                            OutlinedTextField(
                                value = jarTarget,
                                onValueChange = { jarTarget = it },
                                label = { Text(stringResource(R.string.goal_jar_target_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    viewModel.setJarTarget(parseMinor(jarTarget))
                                    jarTarget = ""
                                    editingJar = false
                                },
                            ) {
                                Text(stringResource(R.string.goal_save))
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.padding(top = Spacing.m),
                    ) {
                        PillButton(
                            text = stringResource(R.string.goal_jar_edit),
                            onClick = {
                                editingJar = !editingJar
                                if (editingJar) {
                                    jarTarget = (jar.targetMinor / 100).toString()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = stringResource(R.string.goal_jar_undo),
                            onClick = viewModel::undoLastContribution,
                            color = status.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PillButton(
                        text = stringResource(R.string.goal_jar_delete),
                        onClick = viewModel::deleteJar,
                        color = status.problem,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }
            }

            Text(
                text = stringResource(R.string.goal_stages_title),
                style = MaterialTheme.typography.labelMedium,
                color = status.textSecondary,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    top = Spacing.l,
                    bottom = Spacing.xs,
                ),
            )

            state.stages.forEach { stageState ->
                BolhCard(
                    corner = Radius.block,
                    contentPadding = PaddingValues(
                        start = Spacing.xs,
                        end = Spacing.l,
                        top = Spacing.xs,
                        bottom = Spacing.s,
                    ),
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.xs,
                    ),
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tappable(
                            onClick = { viewModel.toggleStage(stageState.stage) },
                            onLongClick = {
                                pendingStage = stageState.stage.id to stageState.stage.name
                            },
                            longClickLabel = stringResource(R.string.goal_rename),
                        ),
                ) {
                    // Номер этапа: порядок — часть смысла, без него список
                    // читается как набор, а не как путь (§4.1).
                    Text(
                        text = (state.stages.indexOf(stageState) + 1).toString(),
                        style = NumberSmall,
                        color = if (stageState.stage.isDone) {
                            status.textTertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(start = Spacing.s)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(status.surfaceSunken)
                            .wrapContentHeight(),
                    )
                    Checkbox(
                        modifier = Modifier.completionPop(stageState.stage.isDone, peak = 1.28f),
                        checked = stageState.stage.isDone,
                        onCheckedChange = { viewModel.toggleStage(stageState.stage) },
                    )
                    Text(
                        text = stageState.stage.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (stageState.stage.isDone) {
                            status.textTertiary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        textDecoration = if (stageState.stage.isDone) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    stageState.stage.dueDate?.let {
                        Text(
                            text = monthYear(it, locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = status.textTertiary,
                        )
                    }
                }
                stageState.tasks.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tappable(
                                onClick = { viewModel.toggleTask(task) },
                                onLongClick = { pendingTask = task.id to task.name },
                                longClickLabel = stringResource(R.string.goal_rename),
                            )
                            .padding(start = Spacing.xl),
                    ) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = { viewModel.toggleTask(task) },
                            modifier = Modifier.completionPop(task.isDone),
                        )
                        val taskColor by animateColorAsState(
                            targetValue = if (task.isDone) {
                                status.textTertiary
                            } else {
                                status.textSecondary
                            },
                            animationSpec = Motion.color,
                            label = "task-color",
                        )
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = taskColor,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                        )
                    }
                }
                AddTaskRow(onAdd = { viewModel.addTask(stageState.stage.id, it) })
                }
            }

            BolhCard(
                corner = Radius.block,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.s,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newStage,
                        onValueChange = viewModel::setNewStage,
                        label = { Text(stringResource(R.string.goal_stage_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::addStage) {
                        Text(stringResource(R.string.goal_add_stage))
                    }
                }
            }

            if (goal.status == GoalStatus.Done) {
                Text(
                    text = goal.doneAt?.let {
                        stringResource(R.string.goal_completed_at, monthYear(it, locale))
                    }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = status.success,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.xl,
                    ),
                )
                PillButton(
                    text = stringResource(R.string.goal_reopen),
                    onClick = viewModel::reopen,
                    color = status.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screenPadding),
                )
            } else {
                Button(
                    onClick = {
                        viewModel.complete()
                        celebrating = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Ink,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xl),
                ) {
                    Text(stringResource(R.string.goal_complete))
                }
            }

            // Удаление живёт здесь, а не только в правке: достигнутую цель
            // тоже надо чем-то убирать, а в правку за этим никто не идёт.
            PillButton(
                text = stringResource(R.string.goal_delete_goal),
                onClick = { pendingGoal = true },
                color = status.problem,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    bottom = Spacing.xl,
                ),
            )
        }
    }

    // Наложение рисуется после Scaffold: сиблинг до него оказывается под
    // экраном, и празднование никто не видит.
    if (celebrating) {
        CompletionPulse(
            accent = MaterialTheme.colorScheme.primary,
            onFinished = {
                celebrating = false
                onBack()
            },
        )
    }
}

/** Задачи живут только внутри этапа: отдельной вкладки у них нет (§9.1). */
@Composable
private fun AddTaskRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }

    // Поле под каждым этапом превращает список в простыню: пока не позвали —
    // это одна строка текста.
    if (!open) {
        // Строка серого текста не читалась как действие: теперь это кнопка
        // со значком задачи, в том же ритме, что остальные кнопки.
        PillButton(
            text = stringResource(R.string.goal_add_task),
            onClick = { open = true },
            icon = R.drawable.ic_task,
            modifier = Modifier.padding(
                start = Spacing.xl,
                top = Spacing.xs,
                bottom = Spacing.s,
            ),
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.s, bottom = Spacing.s),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.goal_task_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        // Поле остаётся открытым, а пустая задача заводится с именем по
        // умолчанию: человек может набросать пять пустых строк и заполнить их
        // потом, переименовав долгим нажатием.
        val fallback = stringResource(R.string.goal_task_default)
        TextButton(
            onClick = {
                onAdd(text.ifBlank { fallback })
                text = ""
            },
        ) {
            Text(stringResource(R.string.goal_add_task))
        }
    }
}

/** Ввод денег принимает и запятую, и точку: человек пишет как привык. */
private fun parseMinor(text: String): Long = Money.parseMinor(text) ?: 0L

@Composable
private fun InfoBlock(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    BolhCard(
        modifier = Modifier.padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.m,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = titleColor,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        content()
    }
}
