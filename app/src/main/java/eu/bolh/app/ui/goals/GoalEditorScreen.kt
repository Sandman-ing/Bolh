package eu.bolh.app.ui.goals

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.time.ZoneId
import java.time.LocalDate
import java.util.Locale

/**
 * Создание цели (§4.8). Быстрый путь: значок, название, срок. Затем — проверка
 * срока, если коэффициент уже набрался. Этапы можно добавить позже.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalEditorViewModel = viewModel(factory = GoalEditorViewModel.Factory),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val locale = rememberLocale()

    LaunchedEffect(draft.saved) { if (draft.saved) onDone() }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (draft.isEditing) R.string.goal_edit_title else R.string.goal_new_title,
                        ),
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
                modifier = Modifier.padding(vertical = Spacing.s),
            ) {
                items(BolhIcons.goal) { key ->
                    Icon(
                        painter = painterResource(BolhIcons.resFor(key)),
                        contentDescription = null,
                        tint = if (key == draft.icon) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            status.textTertiary
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.setIcon(key) }
                            .padding(10.dp),
                    )
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.goal_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Календарь открывается на сегодняшнем месяце, а не через
                        // полгода: человек ищет свой срок от «сейчас».
                        val today = LocalDate.now()
                        val start = draft.deadline ?: today
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                viewModel.setDeadline(LocalDate.of(year, month + 1, day))
                            },
                            start.year,
                            start.monthValue - 1,
                            start.dayOfMonth,
                        ).apply {
                            // Срок в прошлом бессмыслен: цель к нему уже не успеть.
                            datePicker.minDate = today
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }.show()
                    }
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            ) {
                Text(
                    text = stringResource(R.string.goal_deadline_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = draft.deadline?.let { monthYear(it, locale) }
                        ?: stringResource(R.string.goal_deadline_pick),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            check?.let { DeadlineCheckBlock(check = it, current = draft.deadline, viewModel = viewModel) }

            Text(
                text = stringResource(R.string.goal_stages_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::toggleStages)
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            )

            AnimatedVisibility(visible = draft.stagesExpanded) {
                Column {
                    val stageFallback = stringResource(R.string.goal_stage_default)
                    val taskFallback = stringResource(R.string.goal_task_default)

                    // Список этапов НЕ заворачивается в AnimatedContent: он
                    // сравнивает состояние целиком, а состояние здесь меняется
                    // на каждый набранный символ. Поле пересоздавалось, теряло
                    // фокус, и клавиатура закрывалась после каждой буквы.
                    // Перестановка и так видна: в полях просто меняется текст.
                    Column(modifier = Modifier.animateContentSize(Motion.resize)) {
                    draft.stages.forEachIndexed { index, stage ->
                        BolhCard(
                            corner = Radius.block,
                            modifier = Modifier.padding(
                                horizontal = Spacing.screenPadding,
                                vertical = Spacing.xs,
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Номер этапа: порядок — часть плана, а не украшение.
                                Text(
                                    text = (index + 1).toString(),
                                    style = NumberSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(status.surfaceSunken)
                                        .wrapContentHeight(),
                                )
                                OutlinedTextField(
                                    value = stage.name,
                                    onValueChange = { viewModel.renameStage(index, it) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = Spacing.s),
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = Spacing.xs),
                            ) {
                                // Этап переставляется стрелками, а не перетаскиванием:
                                // список короткий, а перетаскивание в форме мешает вводу.
                                MoveButton(
                                    icon = R.drawable.ic_chevron_up,
                                    enabled = index > 0,
                                    onClick = { viewModel.moveStage(index, -1) },
                                )
                                MoveButton(
                                    icon = R.drawable.ic_chevron_down,
                                    enabled = index < draft.stages.lastIndex,
                                    onClick = { viewModel.moveStage(index, 1) },
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = status.textTertiary,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.removeStage(index) }
                                        .padding(8.dp),
                                )
                            }

                            stage.tasks.forEachIndexed { taskIndex, task ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        start = Spacing.xl,
                                        top = Spacing.xs,
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_task),
                                        contentDescription = null,
                                        tint = status.textTertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    // Имя задачи правится здесь же: набросал
                                    // пустую строку — назвал, когда придумал.
                                    OutlinedTextField(
                                        value = task,
                                        onValueChange = {
                                            viewModel.renameTask(index, taskIndex, it)
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = Spacing.s),
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(
                                            R.string.common_delete,
                                        ),
                                        tint = status.textTertiary,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.removeTask(index, taskIndex) }
                                            .padding(7.dp),
                                    )
                                }
                            }

                            // Задачи заводятся здесь же: раньше за ними надо было
                            // сначала сохранить цель и вернуться внутрь.
                            PillButton(
                                text = stringResource(R.string.goal_add_task),
                                onClick = { viewModel.addTask(index, taskFallback) },
                                icon = R.drawable.ic_task,
                                modifier = Modifier.padding(
                                    start = Spacing.xl,
                                    top = Spacing.s,
                                ),
                            )
                        }
                    }

                    }

                    BolhCard(
                        corner = Radius.block,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPadding,
                            vertical = Spacing.xs,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft.newStage,
                                onValueChange = viewModel::setNewStage,
                                label = { Text(stringResource(R.string.goal_stage_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            // Раньше это была голая надпись, и на фоне поля
                            // ввода она не читалась как кнопка (§9.2).
                            PillButton(
                                text = stringResource(R.string.goal_add_stage),
                                onClick = { viewModel.addStage(stageFallback) },
                                icon = R.drawable.ic_flag,
                                modifier = Modifier.padding(start = Spacing.s),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = draft.canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Ink,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
            ) {
                Text(stringResource(R.string.goal_save))
            }

            if (draft.isEditing) {
                TextButton(
                    onClick = viewModel::delete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenPadding),
                ) {
                    Text(stringResource(R.string.goal_delete), color = status.problem)
                }
            }
        }
    }
}

/**
 * Две равноправные кнопки: «Оставить май» и «Взять ноябрь 2028».
 * Отказ выглядит ровно так же, как согласие, — иначе это не выбор, а давление.
 */
@Composable
private fun DeadlineCheckBlock(
    check: DeadlineCheck,
    current: LocalDate?,
    viewModel: GoalEditorViewModel,
) {
    val status = LocalStatusColors.current
    val locale = rememberLocale()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding)
            .clip(RoundedCornerShape(Radius.card))
            .background(status.surfaceRaised)
            .padding(Spacing.l),
    ) {
        Text(
            text = stringResource(R.string.goal_deadline_check_title),
            style = MaterialTheme.typography.titleMedium,
            color = status.attention,
        )
        Text(
            text = stringResource(
                R.string.goal_deadline_check_text,
                String.format(locale, "%.1f", check.factor),
                check.deadlinesSet,
                check.deadlinesKept,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Spacing.s),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.m),
        ) {
            OutlinedButton(
                onClick = { },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        R.string.goal_keep_deadline,
                        current?.let { monthYear(it, locale) }.orEmpty(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            OutlinedButton(
                onClick = viewModel::acceptRealisticDate,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        R.string.goal_take_deadline,
                        monthYear(check.realistic, locale),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Стрелка перестановки этапа: выключенная гаснет, а не исчезает. */
@Composable
private fun MoveButton(icon: Int, enabled: Boolean, onClick: () -> Unit) {
    val status = LocalStatusColors.current
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (enabled) status.textSecondary else status.divider,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(7.dp),
    )
}
