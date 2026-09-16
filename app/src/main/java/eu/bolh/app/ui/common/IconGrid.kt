package eu.bolh.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing

/**
 * Выбор значка: две строки, листаются вбок.
 *
 * Значков стало много, и в одну строку большая их часть пряталась за краем.
 * В две строки на экране вдвое больше выбора, а выбранный значок обведён
 * и подсвечен, чтобы его было видно издалека.
 */
@Composable
fun IconGrid(
    icons: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val accent = MaterialTheme.colorScheme.primary
    val state = rememberLazyGridState()

    // Выбранный значок сразу на виду, даже если он в конце списка.
    LaunchedEffect(Unit) {
        val index = icons.indexOf(selected)
        if (index >= VISIBLE_WITHOUT_SCROLL) state.scrollToItem(index)
    }

    LazyHorizontalGrid(
        rows = GridCells.Fixed(ROWS),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
        modifier = modifier.height(CELL * ROWS + Spacing.xs * (ROWS - 1)),
    ) {
        items(icons, key = { it }) { key ->
            val isSelected = key == selected
            val tint by animateColorAsState(
                targetValue = if (isSelected) accent else status.textTertiary,
                animationSpec = Motion.color,
                label = "icon-tint",
            )
            val fill by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent,
                animationSpec = Motion.color,
                label = "icon-fill",
            )
            val ring by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.7f) else Color.Transparent,
                animationSpec = Motion.color,
                label = "icon-ring",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(CELL)
                    .clip(CircleShape)
                    .background(fill)
                    .border(width = 1.5.dp, color = ring, shape = CircleShape)
                    .tapIcon { onSelect(key) },
            ) {
                Icon(
                    painter = painterResource(BolhIcons.resFor(key)),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .completionPop(isSelected, peak = 1.15f)
                        .size(24.dp),
                )
            }
        }
    }
}

private val CELL = 48.dp
private const val ROWS = 2
private const val VISIBLE_WITHOUT_SCROLL = 12
