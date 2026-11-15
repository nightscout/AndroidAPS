package app.aaps.pump.carelevo.compose.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun CarelevoActionDialog(
    title: String,
    content: String,
    onDismissRequest: () -> Unit,
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    subContent: String = ""
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (content.isNotBlank()) {
                    Text(
                        text = if (content.contains('<') || content.contains("<br>")) {
                            AnnotatedString.fromHtml(content.replace("\n", "<br>"))
                        } else {
                            AnnotatedString(content)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (subContent.isNotBlank()) {
                    Text(
                        text = subContent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPrimaryClick) {
                Text(text = primaryText)
            }
        },
        dismissButton = {
            if (secondaryText != null && onSecondaryClick != null) {
                TextButton(onClick = onSecondaryClick) {
                    Text(text = secondaryText)
                }
            }
        }
    )
}

@Preview(showBackground = true, name = "Carelevo Action Dialog")
@Composable
private fun CarelevoActionDialogPreview() {
    CarelevoActionDialog(
        title = "Deactivate patch and start new patch?",
        content = "Current patch will be deactivated.",
        subContent = "This dialog preview helps verify current text alignment.",
        onDismissRequest = {},
        primaryText = "Deactivate",
        onPrimaryClick = {},
        secondaryText = "Cancel",
        onSecondaryClick = {}
    )
}
