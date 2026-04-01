package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlin.math.abs

/**
 * Overview graphs section using Vico charts.
 *
 * Pattern: Observe Primary + Sync to Secondary
 * - Each graph has its OWN VicoScrollState and VicoZoomState
 * - BG graph: Interactive - user can scroll/zoom
 * - IOB/COB graphs: Non-interactive - scroll/zoom disabled
 * - LaunchedEffect observes BG graph's state changes and syncs to IOB/COB
 *
 * Synchronization Implementation:
 * - snapshotFlow observes scroll/zoom values from BG graph
 * - debounce(50) waits for gesture to settle
 * - zoomState.zoom() and scrollState.scroll() copy state to secondary graphs
 *
 * Graphs (top to bottom):
 * - BG Graph: Blood glucose readings (200dp height) - Primary interactive
 * - IOB Graph: Insulin on board (75dp height) - Display only, follows BG graph
 * - COB Graph: Carbs on board (75dp height) - Display only, follows BG graph
 */
@OptIn(FlowPreview::class)
@Composable
fun GraphsSection(
    graphViewModel: GraphViewModel,
    modifier: Modifier = Modifier
) {
    // BG graph - primary interactive
    val bgScrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End
    )
    val bgZoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // IOB graph - non-interactive, synced from BG
    val iobScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val iobZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // COB graph - non-interactive, synced from BG
    val cobScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val cobZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // Collect time range ONCE so all graphs use the exact same values in the same frame.
    // Without this, each graph independently collects derivedTimeRange via
    // collectAsStateWithLifecycle(), which can recompose in different frames —
    // causing minTimestamp divergence and scroll misalignment (pixel position
    // maps to different time when x-axis ranges differ).
    val derivedTimeRange by graphViewModel.derivedTimeRange.collectAsStateWithLifecycle()

    // Treatment belt graph - non-interactive, synced from BG
    val beltScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val beltZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // Observe BG graph scroll/zoom and sync to belt/IOB/COB graphs
    LaunchedEffect(bgScrollState, bgZoomState, beltScrollState, beltZoomState, iobScrollState, iobZoomState, cobScrollState, cobZoomState) {
        snapshotFlow { bgScrollState.value to bgZoomState.value }
            .debounce(30) // Wait for gesture to settle
            .collect { (scroll, zoom) ->
                // Sync zoom first, then scroll (order matters for proper positioning)
                beltZoomState.zoom(Zoom.fixed(zoom))
                iobZoomState.zoom(Zoom.fixed(zoom))
                cobZoomState.zoom(Zoom.fixed(zoom))
                delay(10)
                beltScrollState.scroll(Scroll.Absolute.pixels(scroll))
                iobScrollState.scroll(Scroll.Absolute.pixels(scroll))
                cobScrollState.scroll(Scroll.Absolute.pixels(scroll))
            }
    }

    // Auto-scroll to end when new BG value arrives
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    var lastBgTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bgInfoState.bgInfo?.timestamp) {
        val newTimestamp = bgInfoState.bgInfo?.timestamp ?: return@LaunchedEffect
        if (lastBgTimestamp != 0L && newTimestamp > lastBgTimestamp) {
            // New BG arrived - scroll all graphs to show latest data
            bgScrollState.scroll(Scroll.Absolute.End)
        }
        lastBgTimestamp = newTimestamp
    }

    // Correct secondary graph scroll drift — Vico may internally adjust scroll
    // when model producers fire. Watch for any divergence and re-sync to BG.
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                beltScrollState.value to beltZoomState.value,
                iobScrollState.value to iobZoomState.value,
                cobScrollState.value to cobZoomState.value
            )
        }
            .debounce(100) // Let Vico settle after model update
            .collect { (belt, iob, cob) ->
                val bgScroll = bgScrollState.value
                val bgZoom = bgZoomState.value
                val threshold = 1f
                val needsSync =
                    abs(belt.first - bgScroll) > threshold ||
                        abs(iob.first - bgScroll) > threshold ||
                        abs(cob.first - bgScroll) > threshold ||
                        abs(belt.second - bgZoom) > 0.001f ||
                        abs(iob.second - bgZoom) > 0.001f ||
                        abs(cob.second - bgZoom) > 0.001f
                if (needsSync) {
                    beltZoomState.zoom(Zoom.fixed(bgZoom))
                    iobZoomState.zoom(Zoom.fixed(bgZoom))
                    cobZoomState.zoom(Zoom.fixed(bgZoom))
                    delay(10)
                    beltScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    iobScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    cobScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Treatment Belt Graph - running mode background + therapy events
        TreatmentBeltGraphCompose(
            viewModel = graphViewModel,
            scrollState = beltScrollState,
            zoomState = beltZoomState,
            derivedTimeRange = derivedTimeRange,
            modifier = Modifier.fillMaxWidth()
        )
        // BG Graph - primary interactive graph
        BgGraphCompose(
            viewModel = graphViewModel,
            scrollState = bgScrollState,
            zoomState = bgZoomState,
            derivedTimeRange = derivedTimeRange,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
        )
        // IOB Graph - non-interactive, synced from BG graph
        Box(modifier = Modifier.offset(y = (-8).dp)) {
            IobGraphCompose(
                viewModel = graphViewModel,
                scrollState = iobScrollState,
                zoomState = iobZoomState,
                derivedTimeRange = derivedTimeRange,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "IOB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 36.dp, top = 2.dp)
            )
        }
        // COB Graph - non-interactive, synced from BG graph
        Box(modifier = Modifier.offset(y = (-8).dp)) {
            CobGraphCompose(
                viewModel = graphViewModel,
                scrollState = cobScrollState,
                zoomState = cobZoomState,
                derivedTimeRange = derivedTimeRange,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "COB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 36.dp, top = 2.dp)
            )
        }
    }
}
