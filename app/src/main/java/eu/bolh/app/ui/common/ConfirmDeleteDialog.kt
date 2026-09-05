package eu.bolh.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.bolh.app.R
import eu.bolh.app.ui.theme.LocalStatusColors

/**
 * Подтверждение удаления. Удаление в приложении мягкое — строка помечается,
 * а не исчезает, — но человеку об этом знать незачем: для него это «удалить»,
 * и спросить один раз честнее, чем потом объяснять, куда делись данные.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status = LocalStatusColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_delete), color = status.problem)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        containerColor = status.surfaceRaised,
    )
}
