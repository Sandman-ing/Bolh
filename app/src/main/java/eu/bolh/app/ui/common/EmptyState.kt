package eu.bolh.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import eu.bolh.app.ui.theme.LocalStatusColors
import eu.bolh.app.ui.theme.Spacing

/**
 * Онбординга нет — значит пустой экран обязан объяснить себя сам (§9.5).
 * Не «целей нет», а крупная кнопка действия и один пример под ней.
 */
@Composable
fun EmptyState(
    action: String,
    example: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = action, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = example,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalStatusColors.current.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}
