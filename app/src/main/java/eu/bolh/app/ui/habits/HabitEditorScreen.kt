package eu.bolh.app.ui.habits

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.bolh.app.R
import eu.bolh.app.domain.model.HabitKind
import eu.bolh.app.domain.model.HabitMode
import eu.bolh.app.domain.model.ScheduleType
import eu.bolh.app.domain.templates.HabitTemplate
import eu.bolh.app.domain.templates.HabitTemplates
import eu.bolh.app.ui.common.SelectChip
import eu.bolh.app.ui.common.BolhIcons
import eu.bolh.app.ui.common.Hint
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Создание привычки (§5.9). Быстрый путь — три решения: значок и название,
 * полезная или вредная, как часто. Всё остальное живёт под кнопкой расширенных
 * настроек: люди отваливаются не на отметке, а на создании.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitEditorViewModel = viewModel(factory = HabitEditorViewModel.Factory),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current

    LaunchedEffect(draft.saved) {
        if (draft.saved) onDone()
    }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (draft.isEditing) R.string.habit_edit_title else R.string.habit_new_title,
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
        // Смена вида привычки и расписания меняет состав экрана: подписи,
        // выбор дней, набор режимов. Без этого высота скакала, и рамки под
        // пальцем прыгали. animateContentSize, а не AnimatedContent: последний
        // пересоздаёт содержимое и отбирает фокус у полей ввода.
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .animateContentSize(Motion.resize)
                .padding(bottom = Spacing.xxl),
        ) {
            if (!draft.isEditing) {
                TemplatesRow { template, name, minimum, norm ->
                    viewModel.applyTemplate(
                        name = name,
                        icon = template.icon,
                        kind = template.kind,
                        mode = template.mode,
                        schedule = template.schedule,
                        minimumLabel = minimum,
                        normLabel = norm,
                        estimatedMinutes = template.estimatedMinutes,
                        limitPerWeek = template.limitPerWeek,
                    )
                }
                HorizontalDivider(
                    color = status.divider,
                    modifier = Modifier.padding(vertical = Spacing.m),
                )
            }

            IconRow(
                icons = if (draft.kind == HabitKind.Good) BolhIcons.habitGood else BolhIcons.habitBad,
                selected = draft.icon,
                onSelect = viewModel::setIcon,
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.habit_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
            )

            SectionRow {
                BolhChip(
                    text = stringResource(R.string.habit_kind_good),
                    selected = draft.kind == HabitKind.Good,
                    onClick = { viewModel.setKind(HabitKind.Good) },
                )
                BolhChip(
                    text = stringResource(R.string.habit_kind_bad),
                    selected = draft.kind == HabitKind.Bad,
                    onClick = { viewModel.setKind(HabitKind.Bad) },
                )
            }

            SectionRow {
                BolhChip(
                    text = stringResource(R.string.habit_schedule_daily),
                    selected = draft.scheduleType == ScheduleType.Daily,
                    onClick = { viewModel.setScheduleType(ScheduleType.Daily) },
                )
                BolhChip(
                    text = stringResource(R.string.habit_schedule_weekdays),
                    selected = draft.scheduleType == ScheduleType.Weekdays,
                    onClick = { viewModel.setScheduleType(ScheduleType.Weekdays) },
                )
                BolhChip(
                    text = stringResource(R.string.habit_schedule_times),
                    selected = draft.scheduleType == ScheduleType.TimesPerWeek,
                    onClick = { viewModel.setScheduleType(ScheduleType.TimesPerWeek) },
                )
            }

            // Разница между «выбранными днями» и «N раз в неделю» не очевидна
            // из названий, поэтому она подписана прямо под выбором.
            when (draft.scheduleType) {
                ScheduleType.Weekdays -> {
                    ScheduleHint(stringResource(R.string.habit_schedule_weekdays_hint))
                    WeekdayPicker(
                        selected = draft.days,
                        onToggle = viewModel::toggleDay,
                    )
                }

                ScheduleType.TimesPerWeek -> {
                    Stepper(
                        label = stringResource(R.string.habit_schedule_times),
                        value = draft.timesPerWeek,
                        onChange = viewModel::setTimesPerWeek,
                    )
                    ScheduleHint(
                        stringResource(
                            R.string.habit_schedule_times_hint,
                            draft.timesPerWeek,
                        ),
                    )
                }

                ScheduleType.Daily -> Unit
            }

            Text(
                text = stringResource(R.string.habit_advanced),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::toggleAdvanced)
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
            )

            if (draft.advancedExpanded) {
                AdvancedSection(draft = draft, viewModel = viewModel)
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
                Text(stringResource(R.string.habit_save))
            }

            // Гасшая без объяснений кнопка читалась как поломка приложения.
            AnimatedVisibility(
                visible = draft.blocker != null,
                enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
                exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
            ) {
                Hint(
                    text = stringResource(
                        when (draft.blocker) {
                            SaveBlocker.Days -> R.string.habit_blocked_days
                            else -> R.string.habit_blocked_name
                        },
                    ),
                    modifier = Modifier.padding(horizontal = Spacing.screenPadding),
                )
            }

            if (draft.isEditing) {
                TextButton(
                    onClick = viewModel::delete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenPadding),
                ) {
                    Text(stringResource(R.string.habit_delete), color = status.problem)
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(draft: HabitDraft, viewModel: HabitEditorViewModel) {
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val goals by viewModel.availableGoals.collectAsStateWithLifecycle()
    val pause by viewModel.pause.collectAsStateWithLifecycle()
    val locale = rememberLocale()

    // Разрешение спрашивается ровно тогда, когда человек сам захотел напоминание,
    // а не при первом запуске: онбординга в приложении нет (§9.5).
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Text(
        text = stringResource(R.string.habit_mode_title),
        style = MaterialTheme.typography.labelMedium,
        color = status.textTertiary,
        modifier = Modifier.padding(start = Spacing.screenPadding, end = Spacing.screenPadding, bottom = Spacing.xs),
    )

    // Режимы подписаны поведением, а не термином (§5.2).
    draft.availableModes.forEach { mode ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setMode(mode) }
                .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        ) {
            RadioButton(selected = draft.mode == mode, onClick = { viewModel.setMode(mode) })
            Text(
                text = stringResource(modeLabel(mode)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    if (draft.mode == HabitMode.Minimum) {
        OutlinedTextField(
            value = draft.minimumLabel,
            onValueChange = viewModel::setMinimumLabel,
            label = { Text(stringResource(R.string.habit_minimum_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
        )
        OutlinedTextField(
            value = draft.normLabel,
            onValueChange = viewModel::setNormLabel,
            label = { Text(stringResource(R.string.habit_norm_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
        )
    }

    if (draft.mode == HabitMode.Limit) {
        Stepper(
            label = stringResource(R.string.habit_limit_hint),
            value = draft.limitPerWeek,
            onChange = viewModel::setLimitPerWeek,
        )
    }

    if (draft.kind == HabitKind.Bad) {
        NumberField(
            label = stringResource(R.string.habit_cost_hint),
            value = draft.costMinor?.let { (it / 100).toString() }.orEmpty(),
            onValueChange = { text ->
                viewModel.setCostMinor(text.toLongOrNull()?.times(100))
            },
        )
    }

    NumberField(
        label = stringResource(R.string.habit_estimate_hint),
        value = draft.estimatedMinutes?.toString().orEmpty(),
        onValueChange = { text -> viewModel.setEstimatedMinutes(text.toIntOrNull()) },
    )

    if (goals.isNotEmpty()) {
        Text(
            text = stringResource(R.string.habit_link_goal),
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
            items(goals, key = { it.id }) { goal ->
                BolhChip(
                    text = goal.name,
                    selected = draft.goalId == goal.id,
                    onClick = { viewModel.setGoal(goal.id) },
                    icon = goal.icon,
                )
            }
        }
    }

    // Пауза описана последствием, а не термином: человеку важно, что счёт не сломается.
    if (draft.isEditing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val start = pause?.to ?: java.time.LocalDate.now().plusWeeks(1)
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            viewModel.pauseUntil(java.time.LocalDate.of(year, month + 1, day))
                        },
                        start.year,
                        start.monthValue - 1,
                        start.dayOfMonth,
                    ).show()
                }
                .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.habit_pause),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.habit_pause_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.textTertiary,
                )
            }
            Text(
                text = pause?.let {
                    stringResource(
                        R.string.habit_pause_until,
                        it.to.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale)),
                    )
                } ?: stringResource(R.string.habit_pause_none),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pause != null) status.attention else status.textSecondary,
            )
        }
        if (pause != null) {
            Text(
                text = stringResource(R.string.habit_pause_clear),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = viewModel::clearPause)
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val current = draft.reminderMinuteOfDay ?: (20 * 60)
                TimePickerDialog(
                    context,
                    { _, hour, minute -> viewModel.setReminder(hour * 60 + minute) },
                    current / 60,
                    current % 60,
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            }
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
    ) {
        Text(
            text = stringResource(R.string.habit_reminder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = draft.reminderMinuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = status.textSecondary,
        )
        // Напоминание — опция, и выключать его нужно так же просто,
        // как включать. Раньше поставленное время убрать было нечем.
        if (draft.reminderMinuteOfDay != null) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.habit_reminder_off),
                tint = status.textTertiary,
                modifier = Modifier
                    .padding(start = Spacing.s)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.setReminder(null) }
                    .padding(8.dp),
            )
        }
    }

    // Дата начала: по умолчанию сегодня, назад — сколько угодно, вперёд нельзя.
    val startDate = draft.startDate ?: java.time.LocalDate.now()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val today = java.time.LocalDate.now()
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        viewModel.setStartDate(java.time.LocalDate.of(year, month + 1, day))
                    },
                    startDate.year,
                    startDate.monthValue - 1,
                    startDate.dayOfMonth,
                ).apply {
                    datePicker.maxDate = today
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.show()
            }
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
    ) {
        Text(
            text = stringResource(R.string.habit_start_date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = startDate.format(
                java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", locale),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Hint(
            text = stringResource(R.string.habit_start_date_note),
        modifier = Modifier.padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.xs,
        ),
    )

    if (draft.reminderMinuteOfDay != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.habit_reminder_evening),
                style = MaterialTheme.typography.bodyMedium,
                color = status.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = draft.reminderRepeatEvening,
                onCheckedChange = viewModel::setEveningRepeat,
            )
        }
    }
}

@Composable
private fun TemplatesRow(onPick: (HabitTemplate, String, String?, String?) -> Unit) {
    val status = LocalStatusColors.current
    Text(
        text = stringResource(R.string.habit_templates),
        style = MaterialTheme.typography.labelMedium,
        color = status.textTertiary,
        modifier = Modifier.padding(start = Spacing.screenPadding, end = Spacing.screenPadding, bottom = Spacing.s),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
    ) {
        items(HabitTemplates.all, key = { it.key }) { template ->
            // Тексты шаблона разрешаются в композиции: в обработчике нажатия ресурсов уже нет.
            val name = stringResource(template.nameRes)
            val minimum = template.minimumRes?.let { stringResource(it) }
            val norm = template.normRes?.let { stringResource(it) }
            BolhChip(
                text = name,
                selected = false,
                onClick = { onPick(template, name, minimum, norm) },
                icon = template.icon,
            )
        }
    }
}

@Composable
private fun IconRow(icons: List<String>, selected: String, onSelect: (String) -> Unit) {
    val status = LocalStatusColors.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
        modifier = Modifier.padding(vertical = Spacing.s),
    ) {
        items(icons) { key ->
            val isSelected = key == selected
            val press = rememberPressSource()
            val tint by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    status.textTertiary
                },
                animationSpec = Motion.color,
                label = "icon-tint",
            )
            Icon(
                painter = painterResource(BolhIcons.resFor(key)),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(44.dp)
                    .pressScale(press, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = press,
                        indication = ripple(),
                    ) { onSelect(key) }
                    .padding(10.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayPicker(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = rememberLocale()
    // Семь дней в одну строку не влезают ни на одном телефоне — переносим.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
    ) {
        DayOfWeek.entries.forEach { day ->
            BolhChip(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                selected = day in selected,
                onClick = { onToggle(day) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    val status = LocalStatusColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = status.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(value - 1) }) { Text("−") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.s),
        )
        TextButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
    )
}

/**
 * Чипы переносятся по строкам, а не уезжают за край: «N раз в неделю» рядом
 * с двумя другими вариантами в одну строку не помещается.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalStatusColors.current.textTertiary,
        modifier = Modifier.padding(
            start = Spacing.screenPadding,
            end = Spacing.screenPadding,
            bottom = Spacing.xs,
        ),
    )
}

@Composable
private fun SectionRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BolhChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: String? = null,
) {
    val status = LocalStatusColors.current
    val press = rememberPressSource()
    SelectChip(
        text = text,
        selected = selected,
        onClick = onClick,
        icon = icon?.let { BolhIcons.resFor(it) },
    )
}

private fun modeLabel(mode: HabitMode): Int = when (mode) {
    HabitMode.Game -> R.string.habit_mode_game
    HabitMode.Skips -> R.string.habit_mode_skips
    HabitMode.Minimum -> R.string.habit_mode_minimum
    HabitMode.Counter -> R.string.habit_mode_counter
    HabitMode.Limit -> R.string.habit_mode_limit
}
