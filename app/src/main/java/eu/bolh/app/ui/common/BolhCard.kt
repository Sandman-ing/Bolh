package eu.bolh.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.bolh.app.R
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.NumberSmall
import eu.bolh.app.ui.theme.NumberTile
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.TapTargetMin
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
    val press = rememberPressSource()

    // Цвета меняются плавно: статус карточки переключается сам по себе, и
    // мгновенная подмена цвета читается как мигание.
    val fill by animateColorAsState(
        targetValue = if (accent == null) status.surfaceRaised else accent.copy(alpha = 0.10f),
        animationSpec = Motion.color,
        label = "card-fill",
    )
    val stroke by animateColorAsState(
        targetValue = accent ?: status.stroke,
        animationSpec = Motion.color,
        label = "card-stroke",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.pressScale(press))
            .clip(shape)
            .background(fill)
            .border(
                width = if (accent == null) 1.dp else 1.5.dp,
                color = stroke,
                shape = shape,
            )
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = press,
                        indication = ripple(),
                        onClick = onClick,
                    )
                },
            )
            .padding(contentPadding)
            .animateContentSize(animationSpec = Motion.resize),
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
        // Ровно две строки под подпись, даже когда она в одну: иначе
        // «Цели в темпе» переносится, а «Расходы» нет, и числа в соседних
        // плитках стоят на разной высоте — строй ломается.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = status.textSecondary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Сумма ужимается, а не переносится: «8 000 AMD» в трети экрана
        // ломалось на «8 000 A / MD» — перенос шёл посреди кода валюты.
        FittedText(
            text = value,
            style = NumberTile,
            color = valueColor,
            modifier = Modifier.fillMaxWidth(),
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
    /** Поворот значка в градусах: раскрывающая кнопка показывает им своё состояние. */
    iconRotation: Float = 0f,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPressSource()

    @Composable
    fun ButtonIcon() {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(iconRotation),
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(width = 1.dp, color = color.copy(alpha = 0.55f), shape = shape)
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
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
    val press = rememberPressSource()

    val fill by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.14f) else status.surfaceSunken,
        animationSpec = Motion.color,
        label = "chip-fill",
    )
    val border by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.55f) else status.strokeInner,
        animationSpec = Motion.color,
        label = "chip-border",
    )
    val label by animateColorAsState(
        targetValue = if (selected) accent else status.textSecondary,
        animationSpec = Motion.color,
        label = "chip-label",
    )

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(fill)
            .border(width = 1.dp, color = border, shape = shape)
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
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

/**
 * Сноска — пояснение к тому, что рядом.
 *
 * Раньше пояснения жили в таких же рамках, что и кнопки, и на них хотелось
 * нажимать. У сноски рамки нет: только полоска слева и приглушённый текст —
 * так видно, что это подпись, а не действие.
 */
@Composable
fun Hint(
    text: String,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current

    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp, end = Spacing.m)
                .width(2.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(status.strokeInner),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
        )
    }
}

/**
 * Заголовок группы: мелкая надпись над блоком.
 *
 * Значок необязателен, но там, где он есть, он и есть опознавательный знак
 * группы: глаз находит «задачи» по значку быстрее, чем по слову (§9.2).
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    /**
     * Внутри карточки отступы уже есть у неё самой, и собственные превращают
     * заголовок в кусок пустоты над содержимым.
     */
    inCard: Boolean = false,
) {
    val status = LocalStatusColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier.padding(
            start = if (inCard) 0.dp else Spacing.screenPadding,
            top = if (inCard) 0.dp else Spacing.l,
            bottom = Spacing.xs,
        ),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = status.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = status.textTertiary,
        )
    }
}

/**
 * Чип выбора: залит, когда выбран.
 *
 * Заменяет FilterChip из Material. Тот менял заливку и обводку скачком, и на
 * переключении «полезная / вредная» рамка дёргалась. Здесь все три цвета
 * переезжают одним и тем же переходом, а нажатие проминает чип (§9.2).
 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val status = LocalStatusColors.current
    val shape = RoundedCornerShape(percent = 50)
    val press = rememberPressSource()

    val fill by animateColorAsState(
        targetValue = if (selected) accent else Color.Transparent,
        animationSpec = Motion.color,
        label = "select-fill",
    )
    val border by animateColorAsState(
        targetValue = if (selected) accent else status.divider,
        animationSpec = Motion.color,
        label = "select-border",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else status.textSecondary,
        animationSpec = Motion.color,
        label = "select-content",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // По центру: чип бывает шире своей подписи, когда стоит в строке
        // с весом, и прижатый влево текст выглядел бы обрезанным.
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        modifier = modifier
            .pressScale(press)
            .clip(shape)
            .background(fill)
            .border(width = 1.dp, color = border, shape = shape)
            .clickable(interactionSource = press, indication = ripple(), onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Отметка «сделано»: круг, а не квадрат.
 *
 * Квадратный Checkbox из Material читается как ячейка таблицы и живёт своей
 * палитрой — в привычках отметка круглая, и на целях она должна быть такой же,
 * иначе одно и то же действие выглядит двумя разными (§9.2).
 */
@Composable
fun BolhCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    /**
     * Квадрат вместо круга. Этап и задача внутри него — разные вещи, и
     * одинаковыми кружками они сливались в один список. Круг — этап,
     * квадрат — задача внутри него (§9.2).
     */
    square: Boolean = false,
) {
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary
    val press = rememberPressSource()
    val shape = if (square) RoundedCornerShape(size / 3.5f) else CircleShape

    val fill by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        animationSpec = Motion.color,
        label = "check-fill",
    )
    val ring by animateColorAsState(
        targetValue = if (checked) accent else status.divider,
        animationSpec = Motion.color,
        label = "check-ring",
    )

    Box(
        modifier = modifier
            .size(TapTargetMin)
            .clip(CircleShape)
            .clickable(
                interactionSource = press,
                indication = ripple(bounded = false),
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .completionPop(checked)
                .size(size)
                .clip(shape)
                .background(fill)
                .border(width = 2.dp, color = ring, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.62f),
                )
            }
        }
    }
}

/**
 * Кнопка шага «−» и «+».
 *
 * Голый TextButton не читался как кнопка: знак висел в воздухе, попасть по
 * нему было тем ещё делом. Круг той же величины, что и зона нажатия.
 */
@Composable
fun StepButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary
    val press = rememberPressSource()

    val tint by animateColorAsState(
        targetValue = if (enabled) accent else status.textTertiary,
        animationSpec = Motion.color,
        label = "step-tint",
    )

    Box(
        modifier = modifier
            .pressScale(press, pressedScale = 0.88f)
            .size(40.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = tint.copy(alpha = 0.45f), shape = CircleShape)
            .clickable(
                interactionSource = press,
                indication = ripple(),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
        )
    }
}
