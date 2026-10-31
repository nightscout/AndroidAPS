package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem

@Preview(showBackground = true)
@Composable
internal fun AapsClientDetailRowInfoPreview() {
    MaterialTheme {
        AapsClientDetailRow(
            item = AapsClientStatusItem(
                label = "Pump",
                value = "2 min ago",
                level = AapsClientLevel.INFO,
                dialogTitle = "Pump status",
                dialogText = "Last connection: 2 min ago\nReservoir: 120 U\nBattery: 85%"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun AapsClientDetailRowUrgentPreview() {
    MaterialTheme {
        AapsClientDetailRow(
            item = AapsClientStatusItem(
                label = "OpenAPS",
                value = "16 min ago",
                level = AapsClientLevel.URGENT,
                dialogTitle = "OpenAPS",
                dialogText = "Last enacted: 16 min ago"
            )
        )
    }
}
