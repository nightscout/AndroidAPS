package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ThreeButtonDialogPreview() {
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
