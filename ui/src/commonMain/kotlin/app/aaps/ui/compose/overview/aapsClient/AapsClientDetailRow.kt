package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem
import app.aaps.core.ui.compose.AapsTheme

/**
 * Expanded detail row for a single AapsClient status item.
 * Shows the title in bold + detail text below.
 *
 * @see AapsClientDetailRowInfoPreview
 * @see AapsClientDetailRowUrgentPreview
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
