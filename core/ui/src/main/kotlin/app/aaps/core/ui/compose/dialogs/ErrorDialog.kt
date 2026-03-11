package app.aaps.core.ui.compose.dialogs

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * An error/warning dialog with a warning icon, dismiss button, and optional positive button.
 *
 * @param title The dialog title
 * @param message The message to display (supports HTML)
 * @param positiveButton Optional text for positive button (if null, only dismiss is shown)
 * @param onPositive Called when positive button is clicked
 * @param onDismiss Called when dismiss is clicked or dialog is dismissed
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
                text = AnnotatedString.fromHtml(message.replace("\n", "<br>")),
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
                Text(stringResource(R.string.dismiss))
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
                Text(stringResource(R.string.dismiss))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

@Preview(showBackground = true)
@Composable
private fun ErrorDialogPreview() {
    MaterialTheme {
        ErrorDialog(
            title = "Error",
            message = "Something went wrong.\nPlease try again.",
            positiveButton = "Retry",
            onPositive = {},
            onDismiss = {}
        )
    }
}
