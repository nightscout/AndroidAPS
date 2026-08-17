package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun YesNoCancelDialogPreview() {
    MaterialTheme {
        YesNoCancelDialog(
            title = "Save Changes",
            message = "Do you want to save your changes?",
            onYes = {},
            onNo = {},
            onCancel = {}
        )
    }
}
