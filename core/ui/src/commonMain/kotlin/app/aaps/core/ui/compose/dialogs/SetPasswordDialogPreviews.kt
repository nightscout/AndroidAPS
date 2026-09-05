package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun SetPasswordDialogPreview() {
    MaterialTheme {
        SetPasswordDialog(
            title = "Set New Password",
            pinInput = false,
            onConfirm = { _, _ -> },
            onCancel = {}
        )
    }
}
