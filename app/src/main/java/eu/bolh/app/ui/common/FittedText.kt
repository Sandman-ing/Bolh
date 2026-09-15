package eu.bolh.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Число, которое ужимается под свою рамку.
 *
 * Деньги нельзя ни обрезать, ни переносить: «8 000 AMD» в узкой плитке
 * ломалось на «8 000 A / MD», а это уже не сумма, а мусор. Кегль подбирается
 * замером — самый крупный, при котором строка помещается целиком (§9.2).
 *
 * Валюты с длинным кодом и телефоны с узким экраном делают это правилом,
 * а не редким случаем: три знака кода занимают больше места, чем «€».
 *
 * Подбор идёт за один проход разметки. Прежний способ ужимал кегль по кадру
 * и прятал текст до конца подбора, и досчитывающаяся сумма мигала: каждая
 * новая цифра запускала подбор заново.
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
    val maxFontSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp

    Box(modifier = modifier, contentAlignment = align) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            // Ужимать некуда, если нижний кегль не меньше исходного.
            autoSize = if (minFontSize.value < maxFontSize.value) {
                TextAutoSize.StepBased(
                    minFontSize = minFontSize,
                    maxFontSize = maxFontSize,
                    stepSize = 0.5.sp,
                )
            } else {
                null
            },
        )
    }
}
