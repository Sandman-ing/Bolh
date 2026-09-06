package eu.bolh.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.NumberTile
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing

/**
 * Скруглённая карточка с обводкой — основная форма приложения.
 *
 * Всё содержимое живёт внутри рамок: число или строка на голом фоне теряется,
 * потому что глазу не за что зацепиться. Рамка задаёт границу предмета,
 * заливка отделяет его от фона, и вместе они делают экран читаемым (§9.2).
 */
@Composable
fun BolhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    corner: Dp = Radius.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(corner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Карточка со статусом слегка окрашена изнутри: обводки мало,
            // когда нужно сказать «здесь проблема» одним взглядом.
            .background(if (accent == null) status.surfaceRaised else accent.copy(alpha = 0.10f))
            .border(
                width = if (accent == null) 1.dp else 1.5.dp,
                color = accent ?: status.stroke,
                shape = shape,
            )
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Блок с заголовком: рамка, внутри — название раздела своим цветом и содержимое.
 *
 * Заголовок втянут внутрь рамки, а не висит над ней: так видно, где блок
 * начинается и где кончается, даже когда их на экране пять.
 */
@Composable
fun SectionCard(
    title: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    BolhCard(modifier = modifier, contentPadding = contentPadding) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.m),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke(this)
        }
        content()
    }
}

/**
 * Плитка показателя: подпись сверху, число снизу, всё в своей рамке.
 *
 * Числа без рамки на общем фоне сливаются в строку и перестают
 * различаться между собой — а сравнивать их и есть главное занятие (§7).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    hint: String? = null,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.block)

    Column(
        modifier = modifier
            .clip(shape)
            .background(status.surfaceSunken)
            .border(width = 1.dp, color = status.strokeInner, shape = shape)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = status.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Сумме разрешено переноситься: «1 227,50 €» в трети экрана
        // в одну строку не влезает, а обрезать деньги нельзя.
        Text(
            text = value,
            style = NumberTile,
            color = valueColor,
            maxLines = 2,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = status.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Действие в виде обведённой пилюли.
 *
 * Синий текст без границ читается как часть абзаца, а не как кнопка:
 * нажимаемое должно иметь форму.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    iconAtEnd: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val shape = RoundedCornerShape(Radius.control)

    @Composable
    fun ButtonIcon() {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(width = 1.dp, color = color.copy(alpha = 0.55f), shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
    ) {
        if (!iconAtEnd) ButtonIcon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (iconAtEnd) ButtonIcon()
    }
}

/**
 * Чип-переключатель: включён — залит, выключен — только обводка.
 *
 * Бледное слово, меняющее цвет, не читается как переключатель; форма читается.
 */
@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(Radius.chip)

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) accent else status.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else status.surfaceSunken)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f) else status.strokeInner,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    )
}

/**
 * Число в собственной рамке.
 *
 * Проценты и доли, набранные строкой посреди текста, читаются как продолжение
 * фразы и теряются. В рамке число снова становится числом.
 */
@Composable
fun MetricChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(Radius.chip)

    Text(
        text = text,
        style = NumberSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(status.surfaceSunken)
            .border(width = 1.dp, color = status.strokeInner, shape = shape)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    )
}
