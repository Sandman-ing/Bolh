package eu.bolh.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Один шрифт на всё приложение, разный только размер и вес.
 *
 * Числа набраны тем же шрифтом, но с табличными цифрами (`tnum`): колонки суммы
 * выравниваются, как в моноширинном, а текст рядом остаётся человеческим.
 * Настоящий моноширинный здесь пробовали — он растягивает пробелы, и «0 €»
 * разъезжается на полэкрана (документ продукта, §6.8).
 */
private const val TabularFigures = "tnum"

val BolhTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** Главное число экрана: остаток в круге, баланс в шапке. */
val MoneyHero = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 46.sp,
    letterSpacing = (-1).sp,
    fontFeatureSettings = TabularFigures,
    textAlign = TextAlign.Center,
)

/** Крупная сумма: заголовок блока, сумма в шторке ввода. */
val MoneyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.6).sp,
    fontFeatureSettings = TabularFigures,
    textAlign = TextAlign.End,
)

/** Сумма в строке списка. */
val MoneyRow = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    fontFeatureSettings = TabularFigures,
    textAlign = TextAlign.End,
)

/** Число в плитке показателя: доля, темп, счёт дней. */
val NumberTile = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = TabularFigures,
)

/** Мелкое число: процент, счётчик внутри строки. */
val NumberSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = TabularFigures,
)
