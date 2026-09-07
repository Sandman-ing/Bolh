package eu.bolh.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.bolh.app.R
import eu.bolh.app.ui.theme.LocalStatusColors

/**
 * Правка имени с удалением в том же окне.
 *
 * Опечатка не должна стоить пересоздания: раньше этап и задачу можно было
 * только удалить и завести заново, потеряв отметки.
 */
@Composable
fun RenameDialog(
    initial: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status = LocalStatusColors.current
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = status.surfaceRaised,
        title = { Text(stringResource(R.string.goal_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(text)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.goal_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_delete), color = status.problem)
            }
        },
    )
}
