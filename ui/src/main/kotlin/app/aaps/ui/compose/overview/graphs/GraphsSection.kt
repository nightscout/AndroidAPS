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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
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
 * - Secondary graphs: Non-interactive - scroll/zoom disabled
 * - LaunchedEffect observes BG graph's state changes and syncs to secondary
 *
 * Synchronization Implementation:
 * - snapshotFlow observes scroll/zoom values from BG graph
 * - debounce(30) waits for gesture to settle
 * - zoomState.zoom() and scrollState.scroll() copy state to secondary graphs
 *
 * Secondary graphs are config-driven via [GraphConfig.secondaryGraphs].
 * Scroll/Zoom states are pre-allocated (up to [GraphConfig.MAX_SECONDARY_GRAPHS])
 * to avoid dynamic composable state issues with Vico's remember-based states.
 */
@OptIn(FlowPreview::class)
@Composable
fun GraphsSection(
    graphViewModel: GraphViewModel,
    modifier: Modifier = Modifier
) {
    val graphConfig by graphViewModel.graphConfigFlow.collectAsStateWithLifecycle()

    // BG graph - primary interactive
    val bgScrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End
    )
    val bgZoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // Pre-allocate secondary graph scroll/zoom states (up to MAX_SECONDARY_GRAPHS)
    // These are always created to keep Compose's remember slots stable
    val sec0scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec0zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))
    val sec1scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec1zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))
    val sec2scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec2zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))
    val sec3scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec3zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))
    val sec4scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec4zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))

    // Collect nowTimestamp ONCE so all graphs use the same value (avoids separate recompositions every 30s)
    val nowTimestamp by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()

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

    // Active graph count — rememberUpdatedState so coroutines always read the latest value
    // without writing to state during composition. Unattached states are no-ops for
    // .zoom()/.scroll(), but we skip them to avoid redundant calls.
    val activeCount by rememberUpdatedState(
        graphConfig.secondaryGraphs.size.coerceAtMost(GraphConfig.MAX_SECONDARY_GRAPHS)
    )

    // Flag to prevent drift-correction from firing during an active sync
    var isSyncing by remember { mutableStateOf(false) }

    // All secondary scroll/zoom states in arrays for indexed access (keyed to rebuild if state identity changes)
    val secScrollStates = remember(sec0scroll, sec1scroll, sec2scroll, sec3scroll, sec4scroll) {
        arrayOf(sec0scroll, sec1scroll, sec2scroll, sec3scroll, sec4scroll)
    }
    val secZoomStates = remember(sec0zoom, sec1zoom, sec2zoom, sec3zoom, sec4zoom) {
        arrayOf(sec0zoom, sec1zoom, sec2zoom, sec3zoom, sec4zoom)
    }

    // Observe BG graph scroll/zoom and sync to belt + active secondary graphs
    // Keys include ALL state objects — identical pattern to the original working sync
    LaunchedEffect(
        bgScrollState, bgZoomState, beltScrollState, beltZoomState,
        sec0scroll, sec0zoom, sec1scroll, sec1zoom,
        sec2scroll, sec2zoom, sec3scroll, sec3zoom,
        sec4scroll, sec4zoom
    ) {
        snapshotFlow { bgScrollState.value to bgZoomState.value }
            .debounce(30) // Wait for gesture to settle
            .collect { (scroll, zoom) ->
                isSyncing = true
                val count = activeCount
                // Sync zoom first, then scroll (order matters for proper positioning)
                beltZoomState.zoom(Zoom.fixed(zoom))
                for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(zoom))
                delay(10)
                beltScrollState.scroll(Scroll.Absolute.pixels(scroll))
                for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(scroll))
                isSyncing = false
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
    // Skips when isSyncing is true to avoid feedback loop with primary sync above.
    // Key is Unit — runs once for composable lifetime (identical to original pattern)
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Only read states that are attached to a chart (belt + active secondary)
            val count = activeCount
            buildList {
                add(beltScrollState.value to beltZoomState.value)
                for (i in 0 until count) {
                    add(secScrollStates[i].value to secZoomStates[i].value)
                }
            }
        }
            .debounce(100) // Let Vico settle after model update
            .collect { states ->
                if (isSyncing) return@collect
                val bgScroll = bgScrollState.value
                val bgZoom = bgZoomState.value
                val threshold = 1f
                val needsSync = states.any { (scroll, zoom) ->
                    abs(scroll - bgScroll) > threshold || abs(zoom - bgZoom) > 0.001f
                }
                if (needsSync) {
                    isSyncing = true
                    val count = activeCount
                    beltZoomState.zoom(Zoom.fixed(bgZoom))
                    for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(bgZoom))
                    delay(10)
                    beltScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(bgScroll))
                    isSyncing = false
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
            nowTimestamp = nowTimestamp,
            modifier = Modifier.fillMaxWidth()
        )
        // BG Graph - primary interactive graph
        BgGraphCompose(
            viewModel = graphViewModel,
            scrollState = bgScrollState,
            zoomState = bgZoomState,
            derivedTimeRange = derivedTimeRange,
            nowTimestamp = nowTimestamp,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
        )
        // Secondary graphs — config-driven
        for (i in 0 until activeCount) {
            val seriesSet = graphConfig.secondaryGraphs[i]
            Box(modifier = Modifier.offset(y = (-8).dp)) {
                SecondaryGraphCompose(
                    viewModel = graphViewModel,
                    seriesTypes = seriesSet,
                    scrollState = secScrollStates[i],
                    zoomState = secZoomStates[i],
                    derivedTimeRange = derivedTimeRange,
                    nowTimestamp = nowTimestamp,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = seriesSetLabel(seriesSet),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 36.dp, top = 2.dp)
                )
            }
        }
    }
}

// =========================================================================
// Graph label generation
// =========================================================================

/** Generate a short label from the series types in a graph (e.g., "IOB", "COB", "BGI / DEV") */
private fun seriesSetLabel(seriesSet: Set<SeriesType>): String {
    return seriesSet.joinToString(" / ") { seriesShortName(it) }
}

private fun seriesShortName(type: SeriesType): String = when (type) {
    SeriesType.IOB             -> "IOB"
    SeriesType.ABS_IOB         -> "ABS"
    SeriesType.COB             -> "COB"
    SeriesType.BGI             -> "BGI"
    SeriesType.DEVIATIONS      -> "DEV"
    SeriesType.SENSITIVITY     -> "SEN"
    SeriesType.VAR_SENSITIVITY -> "VSENS"
    SeriesType.DEV_SLOPE       -> "SLOPE"
    SeriesType.HEART_RATE      -> "HR"
    SeriesType.STEPS           -> "STEPS"
}
