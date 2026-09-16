package eu.bolh.app.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.BuildConfig
import eu.bolh.app.R
import eu.bolh.app.data.backup.BackupService
import eu.bolh.app.data.backup.NotBolhBackupException
import eu.bolh.app.data.notifications.BolhNotifications
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.data.prefs.LanguagePrefs
import eu.bolh.app.data.prefs.ThemePrefs
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.CurrencySheet
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.common.PillButton
import eu.bolh.app.ui.common.SectionTitle
import eu.bolh.app.ui.common.SystemSettings
import eu.bolh.app.ui.common.pressScale
import eu.bolh.app.ui.common.rememberLocale
import eu.bolh.app.ui.common.rememberPressSource
import eu.bolh.app.ui.common.tapIcon
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import eu.bolh.app.ui.theme.ThemeMode
import java.time.LocalDate
import eu.bolh.app.ui.common.AskNotificationsForReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupMessage {
    data class Saved(val rows: Int) : BackupMessage
    data class Exported(val rows: Int) : BackupMessage
    data class Restored(val rows: Int) : BackupMessage
    data object NotBolh : BackupMessage
    data class Failed(val reason: String) : BackupMessage
}

class SettingsViewModel(private val app: BolhApplication) : ViewModel() {

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    /**
     * Язык из восстановленной копии, если он отличается от текущего.
     *
     * Это разовое событие: модель переживает пересоздание экрана, и
     * неснятое значение после смены языка снова запускало бы пересоздание,
     * и так по кругу.
     */
    private val _restoredLanguage = MutableStateFlow<String?>(null)
    val restoredLanguage: StateFlow<String?> = _restoredLanguage.asStateFlow()

    fun consumeRestoredLanguage() {
        _restoredLanguage.value = null
    }

    fun saveBackup(uri: Uri) = run {
        app.contentResolver.openOutputStream(uri)?.use {
            BackupMessage.Saved(app.container.backupService.exportJson(it))
        }
    }

    fun exportCsv(uri: Uri) = run {
        app.contentResolver.openOutputStream(uri)?.use {
            BackupMessage.Exported(app.container.backupService.exportTransactionsCsv(it))
        }
    }

    fun exportPdf(uri: Uri) = run {
        app.contentResolver.openOutputStream(uri)?.use {
            BackupMessage.Exported(app.container.pdfReport.write(it))
        }
    }

    fun restore(uri: Uri, currentLanguage: String) = run {
        app.contentResolver.openInputStream(uri)?.use { input ->
            val result = app.container.backupService.importJson(input)
            // Настройки из копии применяются сразу, без перезапуска.
            app.container.financeRepository.baseCurrency = CurrencyPrefs.get(app)
            ThemeMode.load(app)
            app.rescheduleReminders()
            // Копия могла принести старые курсы или не принести никаких.
            viewModelScope.launch {
                runCatching { app.container.financeRepository.refreshRatesIfStale() }
            }
            if (result.language != null && result.language != currentLanguage) {
                _restoredLanguage.value = result.language
            }
            BackupMessage.Restored(result.rows)
        }
    }

    private fun run(block: suspend () -> BackupMessage?) {
        viewModelScope.launch {
            _message.value = try {
                block() ?: BackupMessage.Failed(
                    LanguagePrefs.localized(app).getString(R.string.settings_error_no_file),
                )
            } catch (notOurs: NotBolhBackupException) {
                BackupMessage.NotBolh
            } catch (error: Exception) {
                BackupMessage.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication,
                )
            }
        }
    }
}

/**
 * Настройки. Здесь же живёт резервная копия: единственная защита от того,
 * что данные лежат только на устройстве (§8.1).
 *
 * Пояснений здесь нет, кроме инструкции к копии: остальное понятно и так.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restoredLanguage by viewModel.restoredLanguage.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val locale = rememberLocale()
    val today = LocalDate.now()

    var language by remember { mutableStateOf(LanguagePrefs.get(context)) }
    var languagePicker by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(CurrencyPrefs.get(context)) }
    var currencyPicker by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    var switchingLanguage by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val alarms = remember { (context.applicationContext as BolhApplication).container.reminders }
    var exactAllowed by remember { mutableStateOf(alarms.canScheduleExact()) }
    var notificationsOn by remember { mutableStateOf(BolhNotifications.allowed(context)) }
    LifecycleResumeEffect(Unit) {
        exactAllowed = alarms.canScheduleExact()
        notificationsOn = BolhNotifications.allowed(context)
        onPauseOrDispose { }
    }

    // Копия могла принести напоминания, а на новом телефоне уведомления
    // ещё не разрешены. При смене языка спросит уже пересозданный экран.
    AskNotificationsForReminders(
        trigger = (message as? BackupMessage.Restored)?.takeIf { !switchingLanguage && restoredLanguage == null },
    )

    // Копия могла принести другой язык, валюту и тему.
    LaunchedEffect(message) {
        if (message is BackupMessage.Restored) {
            currency = CurrencyPrefs.get(context)
            theme = ThemePrefs.get(context)
        }
    }
    LaunchedEffect(restoredLanguage) {
        restoredLanguage?.let {
            viewModel.consumeRestoredLanguage()
            language = it
            switchingLanguage = true
        }
    }

    if (currencyPicker) {
        CurrencySheet(
            current = currency,
            locale = locale,
            onDismiss = { currencyPicker = false },
            onSelect = { code ->
                CurrencyPrefs.set(context, code)
                // Репозиторий живёт дольше экрана, и валюта в нём — поток:
                // суммы перерисуются сами, без пересоздания экрана.
                (context.applicationContext as BolhApplication)
                    .container
                    .financeRepository
                    .baseCurrency = code
                currency = code
                currencyPicker = false
            },
        )
    }

    if (languagePicker) {
        LanguageSheet(
            current = language,
            onDismiss = { languagePicker = false },
            onSelect = { code ->
                languagePicker = false
                if (code != language) {
                    LanguagePrefs.set(context, code)
                    language = code
                    // Экран гаснет и только потом пересоздаётся: язык
                    // применяется при создании Activity.
                    switchingLanguage = true
                }
            },
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = status.surfaceRaised,
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(stringResource(R.string.settings_restore_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restore(uri, language)
                        pendingRestore = null
                    },
                ) {
                    Text(stringResource(R.string.settings_restore_confirm), color = status.problem)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::saveBackup) }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pendingRestore = it } }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val exportPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let(viewModel::exportPdf) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.action_settings),
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
                            .tapIcon(onClick = onBack)
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            SectionTitle(stringResource(R.string.settings_language))
            ValueRow(
                icon = R.drawable.ic_language,
                value = LanguagePrefs.nameOf(language)
                    ?: stringResource(R.string.settings_language_system),
                onClick = { languagePicker = true },
            )

            SectionTitle(stringResource(R.string.settings_currency))
            ValueRow(
                icon = R.drawable.ic_payments,
                value = "$currency · " + CurrencyPrefs.nameOf(currency, locale),
                onClick = { currencyPicker = true },
            )

            SectionTitle(stringResource(R.string.settings_reminders))
            SettingsRow(
                icon = R.drawable.ic_notifications_active,
                text = stringResource(R.string.settings_reminders_open),
                badge = if (notificationsOn) null else stringResource(R.string.settings_off),
                onClick = { SystemSettings.openNotifications(context) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsRow(
                    icon = R.drawable.ic_schedule,
                    text = stringResource(R.string.settings_reminders_exact),
                    badge = if (exactAllowed) null else stringResource(R.string.settings_not_allowed),
                    onClick = { SystemSettings.openExactAlarms(context) },
                )
            }
            SettingsRow(
                icon = R.drawable.ic_battery_alert,
                text = stringResource(R.string.settings_reminders_battery),
                onClick = { SystemSettings.openBatteryOptimization(context) },
            )

            SectionTitle(stringResource(R.string.settings_appearance))
            ChipRow(
                options = listOf(
                    ThemePrefs.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemePrefs.DARK to stringResource(R.string.settings_theme_dark),
                    ThemePrefs.LIGHT to stringResource(R.string.settings_theme_light),
                ),
                current = theme,
                onSelect = { mode ->
                    if (mode != theme) {
                        // Пересоздания не нужно: тема — это цвета, а не ресурсы.
                        ThemeMode.set(context, mode)
                        theme = mode
                    }
                },
            )

            SectionTitle(stringResource(R.string.settings_backup))
            BolhCard(
                modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_backup),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_backup_howto),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.m),
                    )
                }
                PillButton(
                    text = stringResource(R.string.settings_backup_save),
                    onClick = { saveBackup.launch(BackupService.fileName(today)) },
                    icon = R.drawable.ic_download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.l),
                )
                PillButton(
                    text = stringResource(R.string.settings_backup_restore),
                    onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    icon = R.drawable.ic_restore,
                    color = status.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.s),
                )

                AnimatedContent(
                    targetState = message,
                    transitionSpec = {
                        (fadeIn(tween(Motion.CONTENT_MS)) + expandVertically(Motion.resize)) togetherWith
                            (fadeOut(tween(Motion.CONTENT_MS / 2)) + shrinkVertically(Motion.resize))
                    },
                    label = "backup-message",
                ) { current ->
                    if (current != null) {
                        val failed = current is BackupMessage.Failed || current is BackupMessage.NotBolh
                        Text(
                            text = when (current) {
                                is BackupMessage.Saved -> stringResource(R.string.settings_backup_saved, current.rows)
                                is BackupMessage.Exported -> stringResource(R.string.settings_export_saved, current.rows)
                                is BackupMessage.Restored -> stringResource(R.string.settings_backup_restored, current.rows)
                                BackupMessage.NotBolh -> stringResource(R.string.settings_backup_not_bolh)
                                is BackupMessage.Failed -> stringResource(R.string.settings_error, current.reason)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (failed) status.problem else status.success,
                            modifier = Modifier.padding(top = Spacing.m),
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_reports))
            SettingsRow(
                icon = R.drawable.ic_receipt_long,
                text = stringResource(R.string.settings_export_pdf),
                onClick = { exportPdf.launch("bolh-report-$today.pdf") },
            )
            SettingsRow(
                icon = R.drawable.ic_payments,
                text = stringResource(R.string.settings_export_csv),
                onClick = { exportCsv.launch("bolh-transactions-$today.csv") },
            )

            HorizontalDivider(
                color = status.divider,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.l,
                ),
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            )
        }
    }

    // Затемнение рисуется после Scaffold: сиблингом до него оно оказывалось
    // под экраном. Обратный переход, появление, живёт в MainActivity.
    AnimatedVisibility(
        visible = switchingLanguage,
        enter = fadeIn(tween(0)),
        label = "language-fade",
    ) {
        val fade = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            fade.animateTo(1f, tween(Motion.CONTENT_MS))
            activity?.recreate()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fade.value)
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

/** Настройка с текущим значением: язык, валюта. */
@Composable
private fun ValueRow(icon: Int, value: String, onClick: () -> Unit) {
    BolhCard(
        corner = Radius.block,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
        modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Полоса чипов с одним выбранным. */
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit,
) {
    val status = LocalStatusColors.current

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    ) {
        options.forEach { (code, label) ->
            val selected = code == current
            val press = rememberPressSource()
            val fill by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = Motion.color,
                label = "settings-chip-fill",
            )
            val border by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else status.divider,
                animationSpec = Motion.color,
                label = "settings-chip-border",
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else status.textSecondary,
                animationSpec = Motion.color,
                label = "settings-chip-label",
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier
                    .pressScale(press)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(fill)
                    .border(width = 1.dp, color = border, shape = RoundedCornerShape(percent = 50))
                    .clickable(interactionSource = press, indication = ripple()) { onSelect(code) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: Int, text: String, onClick: () -> Unit, badge: String? = null) {
    val status = LocalStatusColors.current

    // В рамке и со стрелкой: без них строка читалась как заголовок раздела,
    // а не как то, что можно нажать (§9.2).
    BolhCard(
        corner = Radius.block,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
        modifier = Modifier.padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.xs,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m),
            )
            AnimatedVisibility(
                visible = badge != null,
                enter = fadeIn(tween(Motion.CONTENT_MS)),
                exit = fadeOut(tween(Motion.CONTENT_MS / 2)),
                label = "settings-badge",
            ) {
                Text(
                    text = badge.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = status.attention,
                    modifier = Modifier
                        .padding(end = Spacing.s)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(status.attention.copy(alpha = 0.14f))
                        .padding(horizontal = Spacing.s, vertical = 2.dp),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = status.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
