package app.aaps.core.ui.compose.dialogs

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.htmlToAnnotatedString
import app.aaps.core.ui.UiStrings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties

/**
 * A dialog with Yes, No, and Cancel buttons.
 *
 * @param title The dialog title
 * @param message The message to display (supports HTML)
 * @param onYes Called when Yes is clicked
 * @param onNo Called when No is clicked
 * @param onCancel Called when Cancel is clicked or dialog is dismissed
 *
 * @see YesNoCancelDialogPreview
 */
@Composable
fun YesNoCancelDialog(
    title: String,
    message: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(text = message.htmlToAnnotatedString())
        },
        confirmButton = {
            TextButton(onClick = onYes) {
                Text(stringResource(UiStrings.yes))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(UiStrings.cancel))
                }
                TextButton(onClick = onNo) {
                    Text(stringResource(UiStrings.no))
                }
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

/**
 * Overload accepting native [AnnotatedString] — bypasses HTML entirely.
 */
@Composable
fun YesNoCancelDialog(
    title: String,
    message: AnnotatedString,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onYes) {
                Text(stringResource(UiStrings.yes))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(UiStrings.cancel))
                }
                TextButton(onClick = onNo) {
                    Text(stringResource(UiStrings.no))
                }
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}
