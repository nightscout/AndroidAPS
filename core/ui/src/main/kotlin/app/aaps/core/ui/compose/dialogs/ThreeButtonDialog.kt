package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport

/**
 * A confirmation dialog with three stacked, full-width actions: primary, secondary, cancel.
 *
 * Visual hierarchy makes targets unambiguous and easy to hit:
 * - **Primary** ([primaryLabel]) — [FilledTonalButton], filled, the recommended action.
 * - **Secondary** ([secondaryLabel]) — [OutlinedButton], the alternative action.
 * - **Cancel** — [TextButton], dismissive.
 *
 * All three buttons render full-width inside the dialog with 12dp vertical spacing between
 * them. AlertDialog's two action slots are not used: every button lives in `confirmButton`
 * so the layout is stacked rather than the default end-row.
 *
 * @param title Optional dialog title
 * @param message Dialog message (supports HTML)
 * @param secondMessage Optional secondary message in accent color
 * @param icon Optional ImageVector icon
 * @param iconTint Optional tint color for the icon
 * @param primaryLabel Label for the primary action button (e.g., "Skip to Cooldown")
 * @param onPrimary Called when primary button is clicked
 * @param secondaryLabel Label for the alternative action button (e.g., "End")
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
        // All three actions live in confirmButton so AlertDialog renders them as a single
        // stacked column rather than its default end-row. dismissButton is intentionally null.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(primaryLabel)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryLabel)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(resolvedCancel)
                }
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun ThreeButtonDialogPreview() {
    MaterialTheme {
        ThreeButtonDialog(
            title = "End scene",
            message = "Are you sure you want to end Warmup?",
            primaryLabel = "Skip to Cooldown",
            onPrimary = {},
            secondaryLabel = "End",
            onSecondary = {},
            onDismiss = {}
        )
    }
}
