package eu.bolh.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Число, которое ужимается под свою рамку.
 *
 * Деньги нельзя ни обрезать, ни переносить: «8 000 AMD» в узкой плитке
 * ломалось на «8 000 A / MD», а это уже не сумма, а мусор. Кегль подбирается
 * замером — до тех пор, пока строка не поместится целиком (§9.2).
 *
 * Валюты с длинным кодом и телефоны с узким экраном делают это правилом,
 * а не редким случаем: три знака кода занимают больше места, чем «€».
 */
@Composable
fun FittedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    minFontSize: TextUnit = 13.sp,
    align: Alignment = Alignment.CenterStart,
) {
    var size by remember(text, style) { mutableStateOf(style.fontSize) }
    var fitted by remember(text, style) { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = align) {
        Text(
            text = text,
            style = style.copy(fontSize = size, lineHeight = size * 1.18f),
            maxLines = 1,
            softWrap = false,
            // До подбора текст невидим: иначе на первом кадре мелькает
            // крупный вариант и число дёргается на месте.
            color = if (fitted) color else Color.Transparent,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && size > minFontSize) {
                    size *= 0.92f
                } else {
                    fitted = true
                }
            },
        )
    }
}
