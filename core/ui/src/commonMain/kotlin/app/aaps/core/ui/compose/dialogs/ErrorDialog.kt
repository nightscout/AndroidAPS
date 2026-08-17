package app.aaps.core.ui.compose.dialogs

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.htmlToAnnotatedString
import app.aaps.core.ui.UiStrings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties

/**
 * An error/warning dialog with a warning icon, dismiss button, and optional positive button.
 *
 * @param title The dialog title
 * @param message The message to display (supports HTML)
 * @param positiveButton Optional text for positive button (if null, only dismiss is shown)
 * @param onPositive Called when positive button is clicked
 * @param onDismiss Called when dismiss is clicked or dialog is dismissed
 *
 * @see ErrorDialogPreview
 */
@Composable
fun ErrorDialog(
    title: String,
    message: String,
    positiveButton: String? = null,
    onPositive: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message.htmlToAnnotatedString(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            positiveButton?.let {
                TextButton(onClick = onPositive) {
                    Text(positiveButton)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(UiStrings.dismiss))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

/**
 * Overload accepting native [AnnotatedString] — bypasses HTML entirely.
 */
@Composable
fun ErrorDialog(
    title: String,
    message: AnnotatedString,
    positiveButton: String? = null,
    onPositive: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            positiveButton?.let {
                TextButton(onClick = onPositive) {
                    Text(positiveButton)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(UiStrings.dismiss))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}
