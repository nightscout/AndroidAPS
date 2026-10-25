package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.IntOffset
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.IcGlobalClock
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

internal class GraphClockChangeDecoration(
    private val minTimestamp: Long,
    private val changes: List<GraphClockChange>,
    private val color: Color
) : Decoration {

    // Like VisibleRangeHolder, publish after drawing rather than mutating Compose state in a draw.
    val positions = MutableStateFlow<List<Pair<GraphClockChange, Offset>>>(emptyList())

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val visible = mutableListOf<Pair<GraphClockChange, Offset>>()
        with(context) {
            if (ranges.xStep == 0.0) {
                positions.value = emptyList()
                return
            }
            for (change in changes) {
                val x = (change.timestamp - minTimestamp) / 60_000.0
                val canvasX = layerBounds.left + layerDimensions.startPadding +
                    layerDimensions.xSpacing * ((x - ranges.minX) / ranges.xStep).toFloat() - scroll
                if (canvasX < layerBounds.left || canvasX > layerBounds.right) continue
                with(mutableDrawScope) {
                    drawLine(
                        color = color,
                        start = Offset(canvasX, layerBounds.top),
                        end = Offset(canvasX, layerBounds.bottom),
                        strokeWidth = density,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * density, 4 * density))
                    )
                }
                // Keep the touch target inside the plot even when the line is near an edge.
                val halfButton = AapsSpacing.xxLarge.pixels
                val centerX = if (layerBounds.width >= 2 * halfButton)
                    canvasX.coerceIn(layerBounds.left + halfButton, layerBounds.right - halfButton)
                else layerBounds.left + layerBounds.width / 2
                visible += change to Offset(centerX - halfButton, layerBounds.bottom - 2 * halfButton)
            }
        }
        positions.value = visible
    }
}

@Composable
internal fun rememberClockChangeDecoration(minTimestamp: Long, maxTimestamp: Long): GraphClockChangeDecoration {
    val timeZone = TimeZone.currentSystemDefault()
    val changes = remember(minTimestamp, maxTimestamp, timeZone) {
        graphClockChanges(minTimestamp, maxTimestamp, timeZone)
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(minTimestamp, changes, color) { GraphClockChangeDecoration(minTimestamp, changes, color) }
}

/** The glucose chart owns the buttons; all other charts only draw the aligned line. */
@Composable
internal fun GraphClockChangeIndicators(decoration: GraphClockChangeDecoration) {
    val positions by decoration.positions.collectAsState()
    var selected by remember(decoration) { mutableStateOf<GraphClockChange?>(null) }
    for ((change, position) in positions) {
        val description = clockChangeDescription(change)
        IconButton(
            onClick = { selected = change },
            modifier = Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(AapsSpacing.xxLarge * 2)
        ) {
            Icon(
                imageVector = IcGlobalClock,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AapsSpacing.chipIconSize)
            )
        }
    }
    selected?.let { change ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { Icon(IcGlobalClock, contentDescription = null) },
            title = { Text(stringResource(UiStrings.graph_clock_change)) },
            text = { Text(clockChangeDescription(change)) },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text(stringResource(CoreUiStrings.ok)) }
            }
        )
    }
}

@Composable
private fun clockChangeDescription(change: GraphClockChange): String =
    stringResource(
        if (change.minutes > 0) UiStrings.graph_clock_forward else UiStrings.graph_clock_backward,
        abs(change.minutes),
        clockUtcOffset(change.before),
        clockUtcOffset(change.after)
    )
