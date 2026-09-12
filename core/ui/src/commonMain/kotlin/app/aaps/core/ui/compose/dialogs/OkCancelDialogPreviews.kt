package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun OkCancelDialogPreview() {
    MaterialTheme {
        OkCancelDialog(
            title = "Confirmation",
            message = "Are you sure you want to proceed?",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
