package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem
import app.aaps.core.ui.compose.AapsTheme

/**
 * Expanded detail row for a single AapsClient status item.
 * Shows the title in bold + detail text below.
 */
@Composable
fun AapsClientDetailRow(
    item: AapsClientStatusItem,
    modifier: Modifier = Modifier
) {
    val titleColor = when (item.level) {
        AapsClientLevel.INFO   -> MaterialTheme.colorScheme.onSurface
        AapsClientLevel.WARN   -> AapsTheme.generalColors.statusWarning
        AapsClientLevel.URGENT -> AapsTheme.generalColors.statusCritical
    }

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = item.dialogTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor
        )
        Text(
            text = item.dialogText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AapsClientDetailRowInfoPreview() {
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
private fun AapsClientDetailRowUrgentPreview() {
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
