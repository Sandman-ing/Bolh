package eu.bolh.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.bolh.app.R
import eu.bolh.app.data.prefs.LanguagePrefs
import eu.bolh.app.ui.common.BolhCard
import eu.bolh.app.ui.common.Motion
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing

/**
 * Выбор языка тем же листом, что и выбор валюты (§9.6).
 *
 * Языков стало пять, и чипы в ряд перестали помещаться. Название каждого
 * языка написано на нём самом: включив случайно чужой язык, свой найти легко.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val status = LocalStatusColors.current
    val options = listOf(LanguagePrefs.SYSTEM to stringResource(R.string.settings_language_system)) +
        LanguagePrefs.languages

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = status.surfaceRaised) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    bottom = Spacing.xxl,
                ),
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            options.forEach { (code, name) ->
                val selected = code == current
                BolhCard(
                    corner = Radius.block,
                    accent = if (selected) MaterialTheme.colorScheme.primary else null,
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
                    onClick = { onSelect(code) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_language),
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else status.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Spacing.m),
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(Motion.CONTENT_MS)) + scaleIn(tween(Motion.CONTENT_MS)),
                            exit = fadeOut(tween(Motion.CONTENT_MS / 2)) + scaleOut(tween(Motion.CONTENT_MS / 2)),
                            label = "language-check",
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
