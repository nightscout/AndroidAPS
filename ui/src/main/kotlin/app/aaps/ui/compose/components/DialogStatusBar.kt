package app.aaps.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import app.aaps.ui.compose.overview.graphs.CobUiState
import app.aaps.ui.compose.overview.graphs.IobUiState
import app.aaps.core.ui.R as CoreUiR

/**
 * Compact status bar showing BG + trend + delta | IOB | COB.
 * Designed to sit between the top app bar and dialog content.
 */
@Composable
fun DialogStatusBar(
    bgInfo: BgInfoUiState,
    iob: IobUiState,
    cob: CobUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BG section
            val bg = bgInfo.bgInfo
            if (bg != null) {
                val bgColor = bg.bgRange.toColor()
                val bgDecoration = if (bg.isOutdated) TextDecoration.LineThrough else TextDecoration.None
                Text(
                    text = bg.bgText,
                    style = MaterialTheme.typography.titleMedium,
                    color = bgColor,
                    textDecoration = bgDecoration,
                    maxLines = 1
                )
                // Trend arrow
                bg.trendArrow?.let { arrow ->
                    Text(
                        text = arrow.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = bgColor,
                        maxLines = 1
                    )
                }
                // Delta
                bg.deltaText?.let { delta ->
                    Text(
                        text = delta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bgColor,
                        maxLines = 1
                    )
                }
                Separator()
            }

            // IOB section
            if (iob.text.isNotEmpty()) {
                Text(
                    text = stringResource(CoreUiR.string.iob),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = iob.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElementType.INSULIN.color(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Separator()
            }

            // COB section
            if (cob.text.isNotEmpty()) {
                Text(
                    text = stringResource(CoreUiR.string.cob),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = cob.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElementType.CARBS.color(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Separator() {
    Text(
        text = "\u2022",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Composable
private fun BgRange.toColor() = when (this) {
    BgRange.HIGH     -> AapsTheme.generalColors.bgHigh
    BgRange.IN_RANGE -> AapsTheme.generalColors.bgInRange
    BgRange.LOW      -> AapsTheme.generalColors.bgLow
}
