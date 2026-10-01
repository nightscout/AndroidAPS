package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun QuickAddButtonsPreview() {
    MaterialTheme {
        QuickAddButtons(increment1 = 5, increment2 = 10, increment3 = 20, onAddCarbs = {})
    }
}
