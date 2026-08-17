package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun QueryPasswordDialogPreview() {
    MaterialTheme {
        QueryPasswordDialog(
            title = "Enter Password",
            pinInput = false,
            onConfirm = {},
            onCancel = {}
        )
    }
}
