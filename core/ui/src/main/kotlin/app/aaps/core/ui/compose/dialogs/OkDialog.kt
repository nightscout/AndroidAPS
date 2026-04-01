package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * A simple alert dialog with a title, message, and OK button.
 *
 * @param title The dialog title
 * @param message The message to display (supports HTML)
 * @param onDismiss Called when dialog is dismissed
 * @param icon Optional icon displayed above the title
 */
@Composable
fun OkDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(text = AnnotatedString.fromHtml(message.replace("\n", "<br>")))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

/**
 * Overload accepting native [AnnotatedString] — bypasses HTML entirely.
 */
@Composable
fun OkDialog(
    title: String,
    message: AnnotatedString,
    onDismiss: () -> Unit,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

@Preview(showBackground = true)
@Composable
private fun OkDialogPreview() {
    MaterialTheme {
        OkDialog(
            title = "Information",
            message = "Operation completed successfully.",
            onDismiss = {}
        )
    }
}
