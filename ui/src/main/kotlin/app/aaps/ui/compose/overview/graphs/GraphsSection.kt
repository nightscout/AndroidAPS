package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SeriesType
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
/** Fixed graph layout used in simple mode: BG (no overlays), IOB+BAS (no overlays), COB */
private val SIMPLE_MODE_CONFIG = GraphConfig(
    bgOverlays = emptyList(),
    iobOverlays = emptyList(),
    secondaryGraphs = listOf(listOf(SeriesType.COB))
)

/** Series types available for user-configurable secondary graphs (IOB excluded — has dedicated fixed slot) */
private val CONFIGURABLE_SERIES = SeriesType.entries.filter { it != SeriesType.IOB }

@OptIn(FlowPreview::class)
@Composable
fun GraphsSection(
    graphViewModel: GraphViewModel,
    isSimpleMode: Boolean,
    modifier: Modifier = Modifier
) {
    val savedGraphConfig by graphViewModel.graphConfigFlow.collectAsStateWithLifecycle()
    // In simple mode: fixed layout (BG, IOB+BAS, COB — no overlays, no editing)
    val graphConfig = if (isSimpleMode) SIMPLE_MODE_CONFIG else savedGraphConfig

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

    // Fixed IOB graph - non-interactive, synced from BG
    val iobScrollState = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val iobZoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))

    // Active graph count — rememberUpdatedState so coroutines always read the latest value
    // without writing to state during composition. Unattached states are no-ops for
    // .zoom()/.scroll(), but we skip them to avoid redundant calls.
    val activeCount by rememberUpdatedState(
        graphConfig.secondaryGraphs.size.coerceAtMost(GraphConfig.MAX_SECONDARY_GRAPHS)
    )

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
        iobScrollState, iobZoomState,
        sec0scroll, sec0zoom, sec1scroll, sec1zoom,
        sec2scroll, sec2zoom, sec3scroll, sec3zoom,
        sec4scroll, sec4zoom
    ) {
        snapshotFlow { bgScrollState.value to bgZoomState.value }
            .debounce(30) // Wait for gesture to settle
            .collect { (scroll, zoom) ->
                val count = activeCount
                // Sync zoom first, then scroll (order matters for proper positioning)
                beltZoomState.zoom(Zoom.fixed(zoom))
                iobZoomState.zoom(Zoom.fixed(zoom))
                for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(zoom))
                delay(10)
                beltScrollState.scroll(Scroll.Absolute.pixels(scroll))
                iobScrollState.scroll(Scroll.Absolute.pixels(scroll))
                for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(scroll))
            }
    }

    // Auto-scroll when new BG value arrives
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    var lastBgTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bgInfoState.bgInfo?.timestamp) {
        val newTimestamp = bgInfoState.bgInfo?.timestamp ?: return@LaunchedEffect
        if (lastBgTimestamp != 0L && newTimestamp > lastBgTimestamp) {
            val timeRange = derivedTimeRange
            if (predictions.isNotEmpty() && timeRange != null) {
                // Scroll so "now + 2h" is at the right edge of viewport
                val (minTimestamp, _) = timeRange
                val nowX = timestampToX(System.currentTimeMillis(), minTimestamp)
                bgScrollState.animateScroll(Scroll.Absolute.x(nowX + 120.0, bias = 1f))
            } else {
                // No predictions - scroll to end
                bgScrollState.animateScroll(Scroll.Absolute.End)
            }
        }
        lastBgTimestamp = newTimestamp
    }

    // Correct secondary graph scroll drift — Vico may internally adjust scroll
    // when model producers fire. Watch for any divergence and re-sync to BG.
    // No isSyncing guard needed: primary sync only reads BG state, so writing to
    // secondary states here cannot trigger primary sync (no feedback loop).
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Only read states that are attached to a chart (belt + IOB fixed + active secondary)
            val count = activeCount
            buildList {
                add(beltScrollState.value to beltZoomState.value)
                add(iobScrollState.value to iobZoomState.value)
                for (i in 0 until count) {
                    add(secScrollStates[i].value to secZoomStates[i].value)
                }
            }
        }
            .debounce(100) // Let Vico settle after model update
            .collect { states ->
                val bgScroll = bgScrollState.value
                val bgZoom = bgZoomState.value
                val threshold = 1f
                val needsSync = states.any { (scroll, zoom) ->
                    abs(scroll - bgScroll) > threshold || abs(zoom - bgZoom) > 0.001f
                }
                if (needsSync) {
                    val count = activeCount
                    beltZoomState.zoom(Zoom.fixed(bgZoom))
                    iobZoomState.zoom(Zoom.fixed(bgZoom))
                    for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(bgZoom))
                    delay(10)
                    beltScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    iobScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(bgScroll))
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
        var editingBgOverlays by remember { mutableStateOf(false) }
        Box(modifier = Modifier.offset(y = (-16).dp)) {
            BgGraphCompose(
                viewModel = graphViewModel,
                bgOverlays = graphConfig.bgOverlays,
                scrollState = bgScrollState,
                zoomState = bgZoomState,
                derivedTimeRange = derivedTimeRange,
                nowTimestamp = nowTimestamp,
                modifier = Modifier.fillMaxWidth()
            )
            if (!isSimpleMode) {
                GraphEditButton(
                    onClick = { editingBgOverlays = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 2.dp)
                )
            }
        }
        if (editingBgOverlays) {
            GraphSeriesBottomSheet(
                title = stringResource(app.aaps.core.ui.R.string.graph_bg),
                selectedSeries = graphConfig.bgOverlays,
                availableSeries = listOf(SeriesType.ACTIVITY),
                onToggle = { type ->
                    val current = graphConfig.bgOverlays.toMutableList()
                    if (type in current) current.remove(type) else current.add(type)
                    graphViewModel.updateGraphConfig(graphConfig.copy(bgOverlays = current))
                },
                onDismiss = { editingBgOverlays = false }
            )
        }
        // Fixed IOB graph (Graph 1) with optional Activity overlay
        var editingIobOverlays by remember { mutableStateOf(false) }
        Box(modifier = Modifier.offset(y = (-8).dp)) {
            SecondaryGraphCompose(
                viewModel = graphViewModel,
                seriesTypes = listOf(SeriesType.IOB),
                scrollState = iobScrollState,
                zoomState = iobZoomState,
                derivedTimeRange = derivedTimeRange,
                nowTimestamp = nowTimestamp,
                activityOverlay = SeriesType.ACTIVITY in graphConfig.iobOverlays,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(app.aaps.core.ui.R.string.iob) + " / " + stringResource(app.aaps.core.ui.R.string.basal_shortname),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 36.dp, top = 2.dp)
            )
            if (!isSimpleMode) {
                GraphEditButton(
                    onClick = { editingIobOverlays = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 2.dp)
                )
            }
        }
        if (editingIobOverlays) {
            GraphSeriesBottomSheet(
                title = stringResource(app.aaps.core.ui.R.string.iob) + " / " + stringResource(app.aaps.core.ui.R.string.basal_shortname),
                selectedSeries = graphConfig.iobOverlays,
                availableSeries = listOf(SeriesType.ACTIVITY),
                onToggle = { type ->
                    val current = graphConfig.iobOverlays.toMutableList()
                    if (type in current) current.remove(type) else current.add(type)
                    graphViewModel.updateGraphConfig(graphConfig.copy(iobOverlays = current))
                },
                onDismiss = { editingIobOverlays = false }
            )
        }

        // Secondary graphs — config-driven (labels start at "Graph 2")
        var editingGraphIndex by remember { mutableIntStateOf(-1) }
        for (i in 0 until activeCount) {
            val seriesList = graphConfig.secondaryGraphs[i]
            Box(modifier = Modifier.offset(y = (-8).dp)) {
                SecondaryGraphCompose(
                    viewModel = graphViewModel,
                    seriesTypes = seriesList,
                    scrollState = secScrollStates[i],
                    zoomState = secZoomStates[i],
                    derivedTimeRange = derivedTimeRange,
                    nowTimestamp = nowTimestamp,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = seriesListLabel(seriesList),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 36.dp, top = 2.dp)
                )
                if (!isSimpleMode) {
                    GraphEditButton(
                        onClick = { editingGraphIndex = i },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp, top = 2.dp)
                    )
                }
            }
        }
        if (editingGraphIndex >= 0 && editingGraphIndex < activeCount) {
            GraphSeriesBottomSheet(
                title = stringResource(app.aaps.core.ui.R.string.graph_number, editingGraphIndex + 2),
                selectedSeries = graphConfig.secondaryGraphs[editingGraphIndex],
                availableSeries = SeriesType.entries.filter { it != SeriesType.IOB },
                onToggle = { type ->
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    val current = graphs[editingGraphIndex].toMutableList()
                    if (type in current) {
                        current.remove(type)
                    } else {
                        current.add(type)
                        if (current.size > 2) current.removeAt(0) // FIFO: drop oldest
                    }
                    if (current.isEmpty()) {
                        // Auto-remove graph when all series deselected
                        graphs.removeAt(editingGraphIndex)
                        editingGraphIndex = -1
                    } else {
                        graphs[editingGraphIndex] = current
                    }
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onRemoveGraph = {
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    graphs.removeAt(editingGraphIndex)
                    editingGraphIndex = -1
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onDismiss = { editingGraphIndex = -1 }
            )
        }
        // Add graph button (hidden in simple mode)
        if (!isSimpleMode && activeCount < GraphConfig.MAX_SECONDARY_GRAPHS) {
            var showAddSheet by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(app.aaps.core.ui.R.string.graph_add), style = MaterialTheme.typography.labelMedium)
            }
            if (showAddSheet) {
                var newGraphSeries by remember { mutableStateOf(emptyList<SeriesType>()) }
                GraphSeriesBottomSheet(
                    title = stringResource(app.aaps.core.ui.R.string.graph_new),
                    selectedSeries = newGraphSeries,
                    availableSeries = CONFIGURABLE_SERIES,
                    onToggle = { type ->
                        val current = newGraphSeries.toMutableList()
                        if (type in current) {
                            current.remove(type)
                        } else {
                            current.add(type)
                            if (current.size > 2) current.removeAt(0)
                        }
                        newGraphSeries = current
                    },
                    onDismiss = {
                        if (newGraphSeries.isNotEmpty()) {
                            val graphs = graphConfig.secondaryGraphs.toMutableList()
                            graphs.add(newGraphSeries)
                            graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                        }
                        newGraphSeries = emptyList()
                        showAddSheet = false
                    }
                )
            }
        }
        // Spacer so the last graph / Add button isn't covered by QuickLaunch toolbar
        Spacer(Modifier.height(48.dp))
    }
}

// =========================================================================
// Graph label generation
// =========================================================================

/** Generate a short label from the series types in a graph (e.g., "IOB", "COB", "BGI / DEV") */
@Composable
private fun seriesListLabel(seriesList: List<SeriesType>): String {
    val names = seriesList.map { stringResource(seriesShortNameId(it)) }
    return names.joinToString(" / ")
}

/** String resource ID for the short name of a series type */
private fun seriesShortNameId(type: SeriesType): Int = when (type) {
    SeriesType.IOB             -> app.aaps.core.ui.R.string.iob
    SeriesType.ABS_IOB         -> app.aaps.core.ui.R.string.abs_insulin_shortname
    SeriesType.COB             -> app.aaps.core.ui.R.string.cob
    SeriesType.BGI             -> app.aaps.core.ui.R.string.bgi_shortname
    SeriesType.DEVIATIONS      -> app.aaps.core.ui.R.string.deviation_shortname
    SeriesType.SENSITIVITY     -> app.aaps.core.ui.R.string.sensitivity_shortname
    SeriesType.VAR_SENSITIVITY -> app.aaps.core.ui.R.string.variable_sensitivity_shortname
    SeriesType.DEV_SLOPE       -> app.aaps.core.ui.R.string.devslope_shortname
    SeriesType.HEART_RATE      -> app.aaps.core.ui.R.string.heartRate_shortname
    SeriesType.STEPS           -> app.aaps.core.ui.R.string.steps_shortname
    SeriesType.ACTIVITY        -> app.aaps.core.ui.R.string.activity_shortname
}

// =========================================================================
// Graph edit button + series picker bottom sheet
// =========================================================================

/** Small pencil icon button overlaid on a graph */
@Composable
private fun GraphEditButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(28.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Bottom sheet with toggleable FilterChips for series selection + optional remove button */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GraphSeriesBottomSheet(
    title: String,
    selectedSeries: List<SeriesType>,
    availableSeries: List<SeriesType>,
    onToggle: (SeriesType) -> Unit,
    onDismiss: () -> Unit,
    onRemoveGraph: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (onRemoveGraph != null) {
                    TextButton(onClick = onRemoveGraph) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(app.aaps.core.ui.R.string.remove))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                for (type in availableSeries) {
                    FilterChip(
                        selected = type in selectedSeries,
                        onClick = { onToggle(type) },
                        label = { Text(stringResource(seriesShortNameId(type))) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}
