package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun QueryAnyPasswordDialogPreview() {
    MaterialTheme {
        QueryAnyPasswordDialog(
            title = "Import Password",
            passwordExplanation = "Enter the password used to encrypt the export file.",
            passwordWarning = "Warning: incorrect password will result in failed import.",
            onConfirm = {},
            onCancel = {}
        )
    }
}
