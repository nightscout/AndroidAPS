package app.aaps.core.ui.compose.dialogs

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * A confirmation dialog with OK and Cancel buttons.
 *
 * @param title The dialog title (optional)
 * @param message The message to display (supports HTML)
 * @param secondMessage Optional secondary message in accent color
 * @param icon Optional ImageVector icon
 * @param iconTint Optional tint color for the icon
 * @param iconId Optional drawable resource for an icon (deprecated, use [icon] instead)
 * @param onConfirm Called when OK is clicked
 * @param onDismiss Called when Cancel is clicked or dialog is dismissed
 */
@Composable
fun OkCancelDialog(
    title: String? = null,
    message: String,
    secondMessage: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    @DrawableRes iconId: Int? = null, // deprecated: use icon instead
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp)
                )
            }
        } ?: iconId?.let {
            {
                Icon(
                    painter = painterResource(id = iconId),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        },
        title = title?.let {
            {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AnnotatedString.fromHtml(message.replace("\n", "<br>")),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                secondMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = secondMessage,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

/**
 * Overload accepting native [AnnotatedString] — bypasses HTML entirely.
 */
@Composable
fun OkCancelDialog(
    title: String? = null,
    message: AnnotatedString,
    secondMessage: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    @DrawableRes iconId: Int? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp)
                )
            }
        } ?: iconId?.let {
            {
                Icon(
                    painter = painterResource(id = iconId),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        },
        title = title?.let {
            {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                secondMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = secondMessage,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

@Preview(showBackground = true)
@Composable
private fun OkCancelDialogPreview() {
    MaterialTheme {
        OkCancelDialog(
            title = "Confirmation",
            message = "Are you sure you want to proceed?",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
