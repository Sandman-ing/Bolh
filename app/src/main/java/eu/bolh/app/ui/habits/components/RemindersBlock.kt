package eu.bolh.app.ui.habits.components

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import eu.bolh.app.BolhApplication
import eu.bolh.app.R
import eu.bolh.app.data.notifications.BolhNotifications
import eu.bolh.app.data.notifications.ReminderAlarms
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.NoticeBanner
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.SystemSettings
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.common.tapIcon
import eu.bolh.app.ui.common.tappable
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Напоминания привычки (§5.6): сколько угодно времён в день, звук будильника
 * и вечерний повтор.
 *
 * Воду пьют трижды, и напоминать о ней нужно трижды: у конкурентов на это
 * жалуются чаще всего. Здесь каждое время — отдельная плашка, которую можно
 * поправить нажатием или убрать крестиком.
 *
 * Если напоминание не дойдёт — выключены уведомления или нет разрешения на
 * точное время, — об этом сказано прямо здесь, с кнопкой, которая ведёт
 * туда, где это чинится.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemindersBlock(
    times: List<Int>,
    alarm: Boolean,
    eveningRepeat: Boolean,
    onAdd: (Int) -> Unit,
    onChange: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAlarm: (Boolean) -> Unit,
    onEvening: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val locale = rememberLocale()
    val alarms = remember { (context.applicationContext as BolhApplication).container.reminders }
    var exactAllowed by remember { mutableStateOf(alarms.canScheduleExact()) }
    var notificationsOn by remember { mutableStateOf(BolhNotifications.allowed(context)) }

    // Человек уходит в настройки телефона и возвращается: плашки должны
    // сразу увидеть, что разрешение уже дано.
    LifecycleResumeEffect(Unit) {
        exactAllowed = alarms.canScheduleExact()
        notificationsOn = BolhNotifications.allowed(context)
        onPauseOrDispose { }
    }

    // Разрешение спрашивается, когда человек сам захотел напоминание, а не
    // при первом запуске: онбординга в приложении нет (§9.5).
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsOn = BolhNotifications.allowed(context) }

    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    val label = { minute: Int -> LocalTime.of(minute / 60, minute % 60).format(formatter) }

    val pick = { initial: Int, onPicked: (Int) -> Unit ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BolhNotifications.granted(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initial / 60,
            initial % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s)
            .animateContentSize(Motion.resize),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_alarm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.habit_reminders),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.s),
            )
            AnimatedVisibility(
                visible = times.size < ReminderAlarms.MAX_TIMES,
                enter = fadeIn(tween(Motion.CONTENT_MS)) + scaleIn(tween(Motion.CONTENT_MS)),
                exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + scaleOut(tween(Motion.CONTENT_MS / 2)),
                label = "reminder-add",
            ) {
                PillButton(
                    text = stringResource(R.string.habit_reminder_add),
                    onClick = { pick(suggestNext(times)) { onAdd(it) } },
                    icon = R.drawable.ic_plus,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m),
        ) {
            if (times.isEmpty()) {
                Text(
                    text = stringResource(R.string.habit_reminders_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.textTertiary,
                )
            }
            times.forEach { minute ->
                key(minute) {
                    TimeChip(
                        label = label(minute),
                        removeLabel = stringResource(R.string.habit_reminder_remove, label(minute)),
                        onClick = { pick(minute) { onChange(minute, it) } },
                        onRemove = { onRemove(minute) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = times.isNotEmpty(),
            enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
            exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
            label = "reminder-options",
        ) {
            Column {
                SwitchRow(
                    text = stringResource(R.string.habit_reminder_alarm),
                    checked = alarm,
                    onChange = onAlarm,
                )
                SwitchRow(
                    text = stringResource(R.string.habit_reminder_evening),
                    checked = eveningRepeat,
                    onChange = onEvening,
                )
                AnimatedVisibility(
                    visible = !notificationsOn,
                    enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
                    exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
                    label = "reminder-blocked",
                ) {
                    NoticeBanner(
                        text = stringResource(R.string.habit_reminder_blocked),
                        color = status.problem,
                        icon = R.drawable.ic_warning,
                        action = stringResource(R.string.habit_reminder_blocked_action),
                        onAction = { SystemSettings.openNotifications(context) },
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
                AnimatedVisibility(
                    visible = notificationsOn && !exactAllowed,
                    enter = fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize),
                    exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize),
                    label = "reminder-exact",
                ) {
                    NoticeBanner(
                        text = stringResource(R.string.habit_reminder_exact),
                        color = status.attention,
                        icon = R.drawable.ic_schedule,
                        action = stringResource(R.string.habit_reminder_exact_action),
                        onAction = { SystemSettings.openExactAlarms(context) },
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
            }
        }
    }
}

/** Время напоминания: нажатие меняет его, крестик убирает. */
@Composable
private fun TimeChip(
    label: String,
    removeLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(width = 1.dp, color = accent.copy(alpha = 0.55f), shape = shape)
            .tappable(onClick = onClick)
            .padding(start = Spacing.m, end = 2.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_timer),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = NumberSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = accent,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = removeLabel,
            tint = accent,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(32.dp)
                .clip(CircleShape)
                .tapIcon(onClick = onRemove)
                .padding(8.dp),
        )
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val status = LocalStatusColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = status.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = status.textTertiary,
                uncheckedTrackColor = status.surfaceSunken,
                uncheckedBorderColor = status.divider,
            ),
        )
    }
}

/**
 * Какое время предложить следующим: первое напоминание — вечером, каждое
 * следующее — через три часа после последнего, но не позже 23:00.
 */
internal fun suggestNext(times: List<Int>): Int {
    val last = times.maxOrNull() ?: return 20 * 60
    return (last + 3 * 60).coerceAtMost(23 * 60)
}
