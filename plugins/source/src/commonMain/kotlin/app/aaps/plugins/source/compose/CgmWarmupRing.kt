package app.aaps.plugins.source.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Share of the smaller side of the available box the ring takes. */
private const val RING_SIZE_FRACTION = 0.62f

/** Ring thickness as a share of its diameter, so a big ring does not look like a hairline. */
private const val RING_STROKE_FRACTION = 0.065f

/**
 * Warm-up ring, sized from the space it is given instead of from a constant.
 *
 * The ring used to be pinned to `AapsSpacing.bgCircleSize` (126 dp): lost in the middle of a tablet,
 * and in landscape tall enough to push the rest of the screen past the bottom edge. Here the
 * diameter follows the smaller side of the box the caller hands it, clamped between
 * [CgmSpacing.warmupRingMin] and [CgmSpacing.warmupRingMax] so it stays readable at one end and
 * does not become a poster at the other.
 *
 * @param progress 0f..1f for a determinate sweep, or null for a full ring — used when the remaining
 *   time is known but the total is not, so no fraction can honestly be drawn.
 * @param state colours the ring by what it means: working, done, or stopped.
 * @param content drawn in the centre, normally the mm:ss countdown.
 */
@Composable
fun CgmWarmupRing(
    progress: Float?,
    state: CgmUiState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ringColor = when (state) {
        CgmUiState.Ready   -> MaterialTheme.colorScheme.primary
        CgmUiState.Failed  -> MaterialTheme.colorScheme.error
        CgmUiState.Working -> MaterialTheme.colorScheme.tertiary
        CgmUiState.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // In a LazyColumn item maxHeight is unbounded, so the width is what limits the ring there.
        val available = minOf(maxWidth, maxHeight)
        val diameter = (available * RING_SIZE_FRACTION)
            .coerceIn(CgmSpacing.warmupRingMin, CgmSpacing.warmupRingMax)
        val strokeWidth = (diameter * RING_STROKE_FRACTION).coerceIn(6.dp, 14.dp)
        val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
        Box(
            modifier = Modifier.size(diameter),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringDiameter = size.minDimension - strokePx
                val topLeft = Offset((size.width - ringDiameter) / 2f, (size.height - ringDiameter) / 2f)
                val arcSize = Size(ringDiameter, ringDiameter)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = if (progress == null) 360f else 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
            content()
        }
    }
}
