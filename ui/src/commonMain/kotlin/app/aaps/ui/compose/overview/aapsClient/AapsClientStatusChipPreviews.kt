package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem

@Preview(showBackground = true)
@Composable
internal fun AapsClientStatusChipInfoPreview() {
    MaterialTheme {
        AapsClientStatusChip(
            item = AapsClientStatusItem(
                label = "Pump",
                value = "2 min ago",
                level = AapsClientLevel.INFO,
                dialogTitle = "Pump status",
                dialogText = "Last connection: 2 min ago"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun AapsClientStatusChipWarnPreview() {
    MaterialTheme {
        AapsClientStatusChip(
            item = AapsClientStatusItem(
                label = "Uploader",
                value = "48%",
                level = AapsClientLevel.WARN,
                dialogTitle = "Uploader",
                dialogText = "Battery: 48%"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun AapsClientStatusChipUrgentPreview() {
    MaterialTheme {
        AapsClientStatusChip(
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
