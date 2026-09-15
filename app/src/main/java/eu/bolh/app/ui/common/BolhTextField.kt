package eu.bolh.app.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Поле для текста: названий, задач, заметок.
 *
 * Однострочное поле уезжало вбок на длинном тексте, и начало написанного
 * пропадало из виду. Здесь поле растёт вниз вместе с текстом — до шести
 * строк, дальше прокручивается само, — и растёт плавно, а не скачком (§9.2).
 *
 * Перевод строки в названии не нужен никому, поэтому «Ввод» работает как
 * «Готово»: убирает клавиатуру, а не рвёт строку.
 */
@Composable
fun BolhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    maxLines: Int = 6,
) {
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    // Поле, в котором пишут, держится над клавиатурой. Система поднимает его
    // только в миг фокуса, а клавиатура выезжает дольше, и поле растёт позже,
    // с каждой новой строкой, — и новые строки уходили под клавиатуру. Поэтому
    // просьба показать поле целиком повторяется, пока выезжает клавиатура.
    LaunchedEffect(focused) {
        if (focused) {
            snapshotFlow { ime.getBottom(density) }.collectLatest { bringIntoView.bringIntoView() }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            when {
                '\n' !in text -> onValueChange(text)
                // Нажатый «Ввод» — это «Готово», а не новая строка.
                text.length == value.length + 1 -> {
                    onValueChange(text.replace("\n", ""))
                    focus.clearFocus()
                }
                // Вставленный многострочный текст склеивается пробелами, иначе
                // последнее слово строки слиплось бы с первым словом следующей.
                else -> onValueChange(text.replace('\n', ' '))
            }
        },
        label = label,
        textStyle = textStyle,
        singleLine = false,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            // И пока поле растёт: каждая новая строка тоже должна остаться на виду.
            .onSizeChanged { if (focused) scope.launch { bringIntoView.bringIntoView() } }
            .animateContentSize(Motion.resize),
    )
}
