package app.aaps.core.ui.compose.siteRotation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ArrowSelectionDialogPreview() {
    MaterialTheme {
        ArrowSelectionDialog(
            onDismiss = {},
            onArrowSelected = {}
        )
    }
}
