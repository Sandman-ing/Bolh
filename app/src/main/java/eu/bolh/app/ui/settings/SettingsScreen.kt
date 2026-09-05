package eu.bolh.app.ui.settings

import android.content.Context
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.bolh.app.BolhApplication
import eu.bolh.app.BuildConfig
import eu.bolh.app.R
import eu.bolh.app.data.backup.BackupService
import eu.bolh.app.data.backup.PdfReport
import eu.bolh.app.data.prefs.SettingsStore
import eu.bolh.app.ui.theme.Ink
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupMessage {
    data class Exported(val rows: Int) : BackupMessage
    data class Imported(val rows: Int) : BackupMessage
    data class Failed(val reason: String) : BackupMessage
}

class SettingsViewModel(
    private val backup: BackupService,
    private val report: PdfReport,
    private val settings: SettingsStore,
) : ViewModel() {

    val language: StateFlow<String> = settings.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsStore.LANGUAGE_SYSTEM,
    )

    fun setLanguage(code: String) {
        viewModelScope.launch { settings.setLanguage(code) }
    }

    fun exportPdf(context: Context, uri: Uri) = run(context) {
        context.contentResolver.openOutputStream(uri)?.use { BackupMessage.Exported(report.write(it)) }
    }

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun exportJson(context: Context, uri: Uri) = run(context) {
        context.contentResolver.openOutputStream(uri)?.use { BackupMessage.Exported(backup.exportJson(it)) }
    }

    fun exportCsv(context: Context, uri: Uri) = run(context) {
        context.contentResolver.openOutputStream(uri)?.use {
            BackupMessage.Exported(backup.exportTransactionsCsv(it))
        }
    }

    fun importJson(context: Context, uri: Uri) = run(context) {
        context.contentResolver.openInputStream(uri)?.use { BackupMessage.Imported(backup.importJson(it)) }
    }

    private fun run(context: Context, block: suspend () -> BackupMessage?) {
        viewModelScope.launch {
            _message.value = try {
                block() ?: BackupMessage.Failed(context.getString(R.string.settings_error_no_file))
            } catch (error: Exception) {
                BackupMessage.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BolhApplication
                SettingsViewModel(
                    backup = app.container.backupService,
                    report = app.container.pdfReport,
                    settings = app.container.settings,
                )
            }
        }
    }
}

/**
 * Настройки. Здесь же живёт резервная копия — единственная защита от того,
 * что данные лежат только на устройстве (§8.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val context = LocalContext.current
    val stamp = LocalDate.now().toString()

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportJson(context, it) } }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(context, it) } }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importJson(context, it) } }

    val exportPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let { viewModel.exportPdf(context, it) } }

    Scaffold(
        modifier = modifier,
        containerColor = Ink,
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
        Column(modifier = Modifier.padding(innerPadding)) {
            SectionTitle(stringResource(R.string.settings_language))
            LanguageRow(
                current = language,
                onSelect = viewModel::setLanguage,
            )

            SectionTitle(stringResource(R.string.settings_appearance))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.s),
            ) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.settings_theme_dark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.textSecondary,
                )
            }
            Text(
                text = stringResource(R.string.settings_theme_light_soon),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    bottom = Spacing.s,
                ),
            )

            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    top = Spacing.m,
                    bottom = Spacing.xs,
                ),
            )
            Text(
                text = stringResource(R.string.settings_local_note),
                style = MaterialTheme.typography.bodyMedium,
                color = status.textSecondary,
                modifier = Modifier.padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    bottom = Spacing.m,
                ),
            )

            SettingsRow(
                icon = R.drawable.ic_download,
                text = stringResource(R.string.settings_export_json),
                onClick = { exportJson.launch("bolh-$stamp.json") },
            )
            SettingsRow(
                icon = R.drawable.ic_download,
                text = stringResource(R.string.settings_export_csv),
                onClick = { exportCsv.launch("bolh-transactions-$stamp.csv") },
            )
            SettingsRow(
                icon = R.drawable.ic_upload_file,
                text = stringResource(R.string.settings_import),
                onClick = { importJson.launch(arrayOf("application/json", "*/*")) },
            )
            SettingsRow(
                icon = R.drawable.ic_download,
                text = stringResource(R.string.settings_export_pdf),
                onClick = { exportPdf.launch("bolh-$stamp.pdf") },
            )

            message?.let { current ->
                Text(
                    text = when (current) {
                        is BackupMessage.Exported ->
                            stringResource(R.string.settings_export_done, current.rows)

                        is BackupMessage.Imported ->
                            stringResource(R.string.settings_import_done, current.rows)

                        is BackupMessage.Failed ->
                            stringResource(R.string.settings_error, current.reason)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current is BackupMessage.Failed) status.problem else status.success,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPadding,
                        vertical = Spacing.m,
                    ),
                )
            }

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
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalStatusColors.current.textTertiary,
        modifier = Modifier.padding(
            start = Spacing.screenPadding,
            top = Spacing.l,
            bottom = Spacing.xs,
        ),
    )
}

/** Язык переключается здесь же, не выходя в настройки телефона (§9.6). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageRow(current: String, onSelect: (String) -> Unit) {
    val status = LocalStatusColors.current
    val options = listOf(
        SettingsStore.LANGUAGE_SYSTEM to stringResource(R.string.settings_language_system),
        "ru" to stringResource(R.string.settings_language_ru),
        "en" to stringResource(R.string.settings_language_en),
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    ) {
        options.forEach { (code, label) ->
            val selected = code == current
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Ink else status.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else status.divider,
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .clickable { onSelect(code) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: Int, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = Spacing.m),
        )
    }
}
