package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ValueInputDialogPreview() {
    MaterialTheme {
        ValueInputDialog(
            currentValue = 5.0,
            valueRange = 0.0..10.0,
            step = 0.5,
            label = "Insulin",
            unitLabel = TextRef.Literal("U"),
            onValueConfirm = {},
            onDismiss = {}
        )
    }
}
