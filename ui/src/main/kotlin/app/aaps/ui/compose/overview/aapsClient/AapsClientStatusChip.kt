package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem
import app.aaps.core.ui.compose.AapsTheme

/**
 * Compact chip showing "Label: Value" with color-coded value.
 * Used in the collapsed state of [AapsClientStatusCard].
 */
@Composable
fun AapsClientStatusChip(
    item: AapsClientStatusItem,
    modifier: Modifier = Modifier
) {
    val valueColor = when (item.level) {
        AapsClientLevel.INFO   -> MaterialTheme.colorScheme.onSurface
        AapsClientLevel.WARN   -> AapsTheme.generalColors.statusWarning
        AapsClientLevel.URGENT -> AapsTheme.generalColors.statusCritical
    }

    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor)) {
            append("${item.label}: ")
        }
        withStyle(SpanStyle(color = valueColor)) {
            append(item.value)
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun AapsClientStatusChipInfoPreview() {
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
private fun AapsClientStatusChipWarnPreview() {
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
private fun AapsClientStatusChipUrgentPreview() {
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
