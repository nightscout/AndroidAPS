package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * A confirmation dialog with three buttons: cancel, primary action, and secondary action.
 * Buttons are stacked vertically so longer labels (e.g., "Skip to Cooldown") don't truncate.
 *
 * @param title Optional dialog title
 * @param message Dialog message (supports HTML)
 * @param secondMessage Optional secondary message in accent color
 * @param icon Optional ImageVector icon
 * @param iconTint Optional tint color for the icon
 * @param primaryLabel Label for the main action button (e.g., "End")
 * @param onPrimary Called when primary button is clicked
 * @param secondaryLabel Label for the alternative action button (e.g., "Skip to X")
 * @param onSecondary Called when secondary button is clicked
 * @param cancelLabel Optional override for cancel label; defaults to R.string.cancel
 * @param onDismiss Called when cancel is clicked or dialog is dismissed
 */
@Composable
fun ThreeButtonDialog(
    title: String? = null,
    message: String,
    secondMessage: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    cancelLabel: String? = null,
    onDismiss: () -> Unit
) {
    val resolvedCancel = cancelLabel ?: stringResource(R.string.cancel)
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
        // confirmButton hosts both action buttons stacked vertically; dismissButton is the cancel.
        // Primary on top, secondary below — Material 3 stacked-action convention.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                TextButton(onClick = onPrimary) {
                    Text(primaryLabel)
                }
                TextButton(onClick = onSecondary) {
                    Text(secondaryLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(resolvedCancel)
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThreeButtonDialogPreview() {
    MaterialTheme {
        ThreeButtonDialog(
            title = "End scene",
            message = "Are you sure you want to end Warmup?",
            primaryLabel = "End",
            onPrimary = {},
            secondaryLabel = "Skip to Cooldown",
            onSecondary = {},
            onDismiss = {}
        )
    }
}
