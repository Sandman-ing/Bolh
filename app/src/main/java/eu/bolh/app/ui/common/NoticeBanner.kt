package eu.bolh.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import eu.bolh.app.R
import eu.bolh.app.ui.theme.Radius
import eu.bolh.app.ui.theme.Spacing

/**
 * Плашка, которую нельзя не заметить: цветной фон, значок в круге, текст
 * и, если есть что сделать, кнопка.
 *
 * Серые строки мелким шрифтом пролистывали, не читая. Здесь цвет говорит,
 * насколько это важно, раньше, чем прочитан текст (§9.3).
 */
@Composable
fun NoticeBanner(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.ic_info,
    title: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.block)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(width = 1.dp, color = color.copy(alpha = 0.45f), shape = shape)
            .padding(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (action != null && onAction != null) {
            PillButton(
                text = action,
                onClick = onAction,
                color = color,
                modifier = Modifier.padding(start = 44.dp, top = Spacing.s),
            )
        }
    }
}
