package app.aaps.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.extensions.directionToDescription
import app.aaps.core.ui.extensions.directionToIcon
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState

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
        // Read the whole bar as one item instead of up to nine separate stops. No
        // contentDescription here on purpose: merging keeps the texts of the children,
        // including the spoken name of the trend arrow, and setting one would drop them all.
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
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
                // Trend arrow. Draw the icon, not TrendArrow.symbol. That field has no glyph for
                // NONE or for the triple arrows - it holds the placeholders "??" and "X", which
                // users used to see here. The icon has a picture for every value and a spoken
                // name, which a bare arrow character does not.
                bg.trendArrow?.let { arrow ->
                    Icon(
                        imageVector = arrow.directionToIcon(),
                        contentDescription = stringResource(arrow.directionToDescription()),
                        tint = bgColor,
                        // Scale with the text beside it, as the Text this replaced did.
                        modifier = Modifier.size(AapsSpacing.trendArrowSize * LocalAapsScale.current)
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
                    text = stringResource(CoreUiStrings.iob),
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
                    text = stringResource(CoreUiStrings.cob),
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
        // The bullet is only a visual divider, so keep it out of the spoken text.
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clearAndSetSemantics { }
    )
}

@Composable
private fun BgRange.toColor() = when (this) {
    BgRange.HIGH     -> AapsTheme.generalColors.bgHigh
    BgRange.IN_RANGE -> AapsTheme.generalColors.bgInRange
    BgRange.LOW      -> AapsTheme.generalColors.bgLow
}
