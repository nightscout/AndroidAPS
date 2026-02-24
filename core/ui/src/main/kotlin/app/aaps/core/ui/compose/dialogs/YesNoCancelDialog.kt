package app.aaps.core.ui.compose.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme

/**
 * A dialog with Yes, No, and Cancel buttons.
 *
 * @param title The dialog title
 * @param message The message to display (supports HTML)
 * @param onYes Called when Yes is clicked
 * @param onNo Called when No is clicked
 * @param onCancel Called when Cancel is clicked or dialog is dismissed
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
            Column {
                Text(text = AnnotatedString.fromHtml(message.replace("\n", "<br>")))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onNo) {
                        Text(stringResource(R.string.no))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onYes) {
                        Text(stringResource(R.string.yes))
                    }
                }
            }
        },
        confirmButton = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

@Preview(showBackground = true)
@Composable
private fun YesNoCancelDialogPreview() {
    AapsTheme {
        YesNoCancelDialog(
            title = "Save Changes",
            message = "Do you want to save your changes?",
            onYes = {},
            onNo = {},
            onCancel = {}
        )
    }
}
