package eu.bolh.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import eu.bolh.app.R
import eu.bolh.app.data.prefs.CurrencyPrefs
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing
import java.util.Locale

/**
 * Выбор валюты: поиск по коду и названию.
 *
 * Частые валюты стоят первыми, остальные — как их знает система. Пересчёта
 * нет, поэтому выбор здесь означает «в чём я считаю», а не «сколько это в…».
 *
 * Лист общий для настроек и для счёта: в настройках выбирается валюта по
 * умолчанию, у счёта — его собственная (§6.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySheet(
    current: String,
    locale: Locale,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val status = LocalStatusColors.current
    var query by remember { mutableStateOf("") }
    val all = remember { CurrencyPrefs.all() }
    val visible = remember(query, all) {
        if (query.isBlank()) {
            all
        } else {
            val needle = query.trim().lowercase(locale)
            all.filter { code ->
                code.lowercase(locale).contains(needle) ||
                    CurrencyPrefs.nameOf(code, locale).lowercase(locale).contains(needle)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = status.surfaceRaised) {
        // Высота задаётся долей экрана, а не потолком по содержимому. С
        // heightIn список сам решал, сколько занять, и его прокрутка спорила
        // с перетаскиванием шторки: валюты листались через раз, а по карточке
        // не листались вовсе. С weight список получает свою высоту и свой скролл.
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.settings_currency_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.s,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(visible.size) { index ->
                    val code = visible[index]
                    BolhCard(
                        corner = Radius.block,
                        accent = if (code == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            null
                        },
                        contentPadding = PaddingValues(
                            horizontal = Spacing.l,
                            vertical = Spacing.m,
                        ),
                        onClick = { onSelect(code) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = code,
                                style = NumberSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = CurrencyPrefs.nameOf(code, locale),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = Spacing.m),
                            )
                        }
                    }
                }
            }
        }
    }
}
