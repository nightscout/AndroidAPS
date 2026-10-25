package app.aaps.ui.compose.overview.graphs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.aaps.core.ui.compose.AapsSpacing
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal class GraphClockChangeDecoration(
    private val minTimestamp: Long,
    private val changes: List<GraphClockChange>,
    private val color: Color,
    private val icon: PainterComponent?
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            if (ranges.xStep == 0.0) return
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
                icon?.let {
                    val size = minOf(AapsSpacing.chipIconSize.pixels, layerBounds.width, layerBounds.height)
                    val gap = AapsSpacing.small.pixels
                    val left = (if (canvasX + gap + size <= layerBounds.right) canvasX + gap else canvasX - gap - size)
                        .coerceIn(layerBounds.left, layerBounds.right - size)
                    it.draw(context, left, layerBounds.bottom - size, left + size, layerBounds.bottom)
                }
            }
        }
    }
}

@Composable
internal fun rememberClockChangeDecoration(minTimestamp: Long, maxTimestamp: Long, showIcon: Boolean = false): GraphClockChangeDecoration {
    val timeZone = TimeZone.currentSystemDefault()
    val changes = remember(minTimestamp, maxTimestamp, timeZone) {
        graphClockChanges(minTimestamp, maxTimestamp, timeZone)
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val painter = if (showIcon) rememberVectorPainter(Icons.Filled.Schedule) else null
    return remember(minTimestamp, changes, color, painter) {
        GraphClockChangeDecoration(minTimestamp, changes, color, painter?.let { PainterComponent(it, color) })
    }
}

private const val HOUR_MS = 3_600_000L

internal data class GraphClockChange(
    val timestamp: Long,
    val before: UtcOffset,
    val after: UtcOffset
) {
    val minutes: Int get() = (after.totalSeconds - before.totalSeconds) / 60
}

/** Finds offset changes in the axis time zone without changing elapsed-time coordinates. */
internal fun graphClockChanges(start: Long, end: Long, timeZone: TimeZone): List<GraphClockChange> {
    if (end <= start) return emptyList()
    val changes = mutableListOf<GraphClockChange>()
    var cursor = start
    var offset = timeZone.offsetAt(Instant.fromEpochMilliseconds(start - 1))
    while (cursor <= end) {
        val next = minOf(cursor + HOUR_MS, end)
        val nextOffset = timeZone.offsetAt(Instant.fromEpochMilliseconds(next))
        if (offset != nextOffset) {
            var low = cursor
            var high = next
            while (low < high) {
                val middle = low + (high - low) / 2
                if (timeZone.offsetAt(Instant.fromEpochMilliseconds(middle)) == offset) low = middle + 1
                else high = middle
            }
            changes += GraphClockChange(low, offset, nextOffset)
        }
        if (next == end) break
        cursor = next
        offset = nextOffset
    }
    return changes
}

internal fun graphTimeLabel(timestamp: Long, timeZone: TimeZone): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val local = instant.toLocalDateTime(timeZone)
    return buildString {
        append(local.hour.toString().padStart(2, '0'))
        if (local.minute != 0) {
            append(':')
            append(local.minute.toString().padStart(2, '0'))
        }
    }
}
