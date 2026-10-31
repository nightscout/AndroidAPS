package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusData
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
internal fun AapsClientStatusCardCollapsedPreview() {
    MaterialTheme {
        AapsClientStatusCard(
            statusData = AapsClientStatusData(
                pump = AapsClientStatusItem(
                    label = "Pump",
                    value = "2 min ago",
                    level = AapsClientLevel.INFO,
                    dialogTitle = "Pump status",
                    dialogText = "Last connection: 2 min ago\nReservoir: 120 U"
                ),
                openAps = AapsClientStatusItem(
                    label = "OpenAPS",
                    value = "1 min ago",
                    level = AapsClientLevel.INFO,
                    dialogTitle = "OpenAPS",
                    dialogText = "Last enacted: 1 min ago"
                ),
                uploader = AapsClientStatusItem(
                    label = "Uploader",
                    value = "85%",
                    level = AapsClientLevel.INFO,
                    dialogTitle = "Uploader",
                    dialogText = "Battery: 85%"
                )
            ),
            flavorTint = Color(0x40FF9800)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
internal fun AapsClientStatusCardMixedLevelsPreview() {
    MaterialTheme {
        AapsClientStatusCard(
            statusData = AapsClientStatusData(
                pump = AapsClientStatusItem(
                    label = "Pump",
                    value = "12 min ago",
                    level = AapsClientLevel.WARN,
                    dialogTitle = "Pump status",
                    dialogText = "Last connection: 12 min ago\nReservoir: 45 U"
                ),
                openAps = AapsClientStatusItem(
                    label = "OpenAPS",
                    value = "16 min ago",
                    level = AapsClientLevel.URGENT,
                    dialogTitle = "OpenAPS",
                    dialogText = "Last enacted: 16 min ago"
                )
            ),
            flavorTint = Color(0x40FF9800)
        )
    }
}
