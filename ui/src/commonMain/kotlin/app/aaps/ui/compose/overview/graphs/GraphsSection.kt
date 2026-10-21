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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.rowAction
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.conflate
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
 * - conflate() takes the newest position on every frame
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
    secondaryGraphs = listOf(SecondaryGraph(listOf(SeriesType.COB)))
)

/**
 * How many recent readings the spoken description of the BG graph lists.
 *
 * Five is about twenty-five minutes, which is enough to hear where the glucose is heading without
 * turning the description into a list nobody will sit through.
 */
private const val RECENT_VALUES_SPOKEN = 5

/** Moves the pill up over the treatment belt (48 dp, of which 16 dp overlap the BG graph). */
private val INSPECT_PILL_OFFSET = (-32).dp

/**
 * The space the time labels took under each graph below BG, before they were removed (measured: the plot
 * grew by 27 px at 420 dpi). These graphs are drawn this much shorter than their height setting, so the
 * plot keeps its size and the whole section gets shorter.
 */
private val REMOVED_TIME_LABELS_HEIGHT = 10.dp

/** Size of the edit button (pencil) on a graph. The icon in it is 16 dp. */
private val GRAPH_EDIT_BUTTON_SIZE = 28.dp

/**
 * Puts the name and the pencil of a graph below BG just above the bottom line of its plot, in the bottom right
 * corner. The line is about 12 dp above the bottom of the graph (room for half of the lowest Y-axis label). The
 * row is 28 dp tall and the text and icon are 16 dp in its middle, so they end 8 + 6 = 14 dp above the bottom.
 */
private val GRAPH_NAME_BOTTOM_PADDING = 8.dp

/**
 * How far the IOB graph is pulled up towards BG. The BG graph is drawn 16 dp higher than its place (over the
 * treatment belt), so the same 16 dp puts the IOB graph right below it, without an empty band in between.
 */
private val IOB_GRAPH_PULL_UP = 16.dp

/**
 * How much each further graph overlaps the one above it. Vico keeps half a label height free above and below
 * each plot, for labels on its top and bottom lines. 5 dp of these two free bands can overlap, and two such
 * labels still do not touch.
 */
private val GRAPH_OVERLAP = 5.dp

/**
 * Draws the content [amount] higher and makes it take [amount] less height, so everything below it moves up
 * too. Modifier.offset only moves the drawing and leaves the space empty.
 */
private fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pull = amount.roundToPx()
    layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) {
        placeable.place(0, -pull)
    }
}

/** Which way a predicted curve is travelling, which decides the sentence spoken for it. */
internal enum class PredictionDirection { UP, DOWN, FLAT }

/**
 * The point of a predicted curve worth saying out loud, and which way it lies from [current].
 *
 * The furthest excursion from the current glucose, not the last point: a curve that dips to 3.2
 * before settling at 6.0 matters for the dip, and "heading down to" is how a user describes it.
 * A curve that only rises therefore reports its peak, one that only falls reports its trough, and
 * one that goes nowhere reports the value it holds.
 *
 * [values] must already be in the user's units. Returns null for an empty curve.
 */
internal fun predictionExtreme(values: List<Double>, current: Double): Pair<Double, PredictionDirection>? {
    if (values.isEmpty()) return null
    val highest = values.max()
    val lowest = values.min()
    val up = highest - current
    val down = current - lowest
    return when {
        up > down -> highest to PredictionDirection.UP
        down > up -> lowest to PredictionDirection.DOWN
        else      -> current to PredictionDirection.FLAT
    }
}

/**
 * The predicted curves in the order they are spoken, with the name each is spoken by.
 *
 * Carbs first because a meal is what a user is most often checking on, then insulin, then the two
 * that only appear in some setups. Absorbed COB exists on AMA only and is simply absent otherwise.
 */
private val PREDICTION_SPOKEN_ORDER = listOf(
    BgType.COB_PREDICTION to UiStrings.a11y_prediction_name_cob,
    BgType.IOB_PREDICTION to UiStrings.a11y_prediction_name_iob,
    BgType.A_COB_PREDICTION to UiStrings.a11y_prediction_name_acob,
    BgType.UAM_PREDICTION to UiStrings.a11y_prediction_name_uam,
    BgType.ZT_PREDICTION to UiStrings.a11y_prediction_name_zt
)

/** Series types available as BG graph overlays */
private val BG_OVERLAY_SERIES = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS)

/** Series types available for user-configurable secondary graphs (IOB + UI-only overlays excluded) */
private val CONFIGURABLE_SERIES = SeriesType.entries.filter {
    it != SeriesType.IOB && it != SeriesType.PREDICTIONS
}

@OptIn(FlowPreview::class)
@Composable
fun GraphsSection(
    graphViewModel: GraphViewModel,
    isSimpleMode: Boolean,
    modifier: Modifier = Modifier,
    fitWholeWindow: Boolean = false,
    /**
     * Insulin and carbs on board, already formatted, for the spoken description of the BG graph.
     * The overview passes them; the history browser must NOT, because these are CURRENT values and
     * announcing them beside a past day would state something untrue about insulin.
     */
    iobText: String? = null,
    cobText: String? = null
) {
    val dateUtil = LocalDateUtil.current
    val savedGraphConfig by graphViewModel.graphConfigFlow.collectAsStateWithLifecycle()
    // In simple mode: fixed layout (BG, IOB+BAS, COB — no overlays, no editing)
    val graphConfig = if (isSimpleMode) SIMPLE_MODE_CONFIG else savedGraphConfig

    // Zoom.x(...) allocates a fresh (non-equal, unmemoized) lambda instance on every call.
    // rememberVicoZoomState's underlying rememberSaveable is keyed on initialZoom/minZoom/maxZoom
    // by reference, so passing a freshly-allocated Zoom on every recomposition of this composable
    // tears down and recreates the VicoZoomState — resetting the zoom level back to initialZoom.
    // Memoize these once so all zoom states below stay stable across recompositions.
    val defaultZoom = remember { Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES) }
    val bgMinZoom = remember { Zoom.x(Constants.GRAPH_TIME_RANGE_HOURS * 60.0) }
    val bgMaxZoom = remember { Zoom.x(MIN_GRAPH_ZOOM_MINUTES) }

    // Where the graphs start. The overview is a live view, so the last six hours anchored at the
    // right edge is what the user wants. The history browser shows one chosen day, and its window
    // ends at that day's midnight - the same six hours would be the late evening only, and for
    // today it would be hours that have not happened yet, which reads as an empty graph. There it
    // starts fully zoomed out, which is exactly the day, because the view model spans the axis over
    // the whole window in that mode. Both pick one of the zoom objects above rather than allocating
    // a new one - see the note above on why a fresh Zoom instance tears the state down.
    val startZoom = if (fitWholeWindow) bgMinZoom else defaultZoom

    // BG graph - primary interactive.
    // Wrapped in key(bgViewportResetTrigger) so bumping the trigger tears down and recreates both
    // states from scratch, snapping them back to their initial values (Scroll.Absolute.End /
    // startZoom). This is deliberate: VicoZoomState.zoom(Zoom) is pinch-gesture-oriented
    // (it applies a ratio anchored on the current canvas center via an async pendingScroll flow)
    // and produced a wrong end state when used to "reset to a fixed default" — recreating the
    // state objects is simpler and matches the exact positioning Vico itself already uses on first
    // composition. Only reset on real inactivity (rare, never mid-gesture), so this doesn't
    // reintroduce the "churn during an active gesture" issues found elsewhere in this file.
    // IMPORTANT: every effect below that reads bgScrollState/bgZoomState.value MUST be keyed on
    // them (not just Unit) — otherwise it keeps a stale reference to the abandoned pre-reset
    // objects forever, comparing live secondary-graph state against a frozen stale value and
    // firing wrong corrections (this caused the secondary graphs to visibly "dance"/flash on the
    // first attempt at this).
    var bgViewportResetTrigger by remember { mutableIntStateOf(0) }
    val (bgScrollState, bgZoomState) = key(bgViewportResetTrigger) {
        rememberVicoScrollState(
            scrollEnabled = true,
            initialScroll = Scroll.Absolute.End
        ) to rememberVicoZoomState(
            zoomEnabled = true,
            initialZoom = startZoom,
            minZoom = bgMinZoom,
            maxZoom = bgMaxZoom
        )
    }

    // Pre-allocate secondary graph scroll/zoom states (up to MAX_SECONDARY_GRAPHS)
    // These are always created to keep Compose's remember slots stable
    val sec0scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec0zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec1scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec1zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec2scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec2zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec3scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec3zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)
    val sec4scroll = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val sec4zoom = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)

    // Collect nowTimestamp ONCE so all graphs use the same value (avoids separate recompositions every 30s)
    val nowTimestamp by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()

    // Collect time range ONCE so all graphs use the exact same values in the same frame.
    // Without this, each graph independently collects derivedTimeRange via
    // collectAsStateWithLifecycle(), which can recompose in different frames —
    // causing minTimestamp divergence and scroll misalignment (pixel position
    // maps to different time when x-axis ranges differ).
    val derivedTimeRange by graphViewModel.derivedTimeRange.collectAsStateWithLifecycle()

    // BG's own visible window, sourced from the fixed IOB graph's already-computed visible range
    // (its scroll/zoom are synced to BG's, see the sync LaunchedEffect below) — not from a
    // decoration on BG's own chart, since that was tried and found to break BG's pinch-zoom
    // gesture handling (BG is the only graph with live scrollEnabled/zoomEnabled = true).
    var iobVisibleRange by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Settle further before feeding into BG specifically: unlike the secondary graphs (which are
    // non-interactive and unaffected by frequent updates), BG has live pinch-zoom. Updating its
    // axis range on every ~80ms tick during an active gesture kept changing the Y-axis label
    // width/layerDimensions mid-gesture and broke pinch-zoom. Only update once the window has
    // been stable for a bit, i.e. after the gesture ends.
    var iobVisibleRangeSettled by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { iobVisibleRange }
            .debounce(400)
            .collect { iobVisibleRangeSettled = it }
    }

    val bgVisibleTimeRange = derivedTimeRange?.first?.let { minTs ->
        iobVisibleRangeSettled?.let { (minXv, maxXv) ->
            (minTs + (minXv * 60_000).toLong()) to (minTs + (maxXv * 60_000).toLong())
        }
    }

    // Treatment belt graph - non-interactive, synced from BG
    val beltScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val beltZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = startZoom
    )

    // Fixed IOB graph - non-interactive, synced from BG
    val iobScrollState = rememberVicoScrollState(scrollEnabled = false, initialScroll = Scroll.Absolute.End)
    val iobZoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = startZoom)

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
        // Do not report a user interaction from here: the scroll/zoom values also change without the user
        // (viewport reset on a new BG, the prediction nudge, Vico's initial scroll to the end). Real gestures
        // are reported by onScrollOrZoomGesture on the BG chart.
        var syncedZoom: Float? = null
        var syncedCount = -1
        snapshotFlow { bgScrollState.value to bgZoomState.value }
            .conflate()
            .collect { (scroll, zoom) ->
                val count = activeCount
                // Zoom first, then scroll, with no wait in between. Vico's zoom() also scrolls the graph,
                // around its own centre and starting from the scroll it last drew, and that scroll is done
                // when zoom() returns. Our scroll corrects it before the next frame is drawn. A wait here
                // let a frame be drawn with Vico's scroll, the next zoom() started from that wrong value,
                // and during a pinch the graphs drifted sideways (up to an hour near the edge).
                if (zoom != syncedZoom || count != syncedCount) {
                    beltZoomState.zoom(Zoom.fixed(zoom))
                    iobZoomState.zoom(Zoom.fixed(zoom))
                    for (i in 0 until count) secZoomStates[i].zoom(Zoom.fixed(zoom))
                    syncedZoom = zoom
                    syncedCount = count
                }
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
            // Skip auto-reset while user is interacting with the graph
            val sinceInteraction = dateUtil.now() - graphViewModel.lastInteractionMs
            if (sinceInteraction < INTERACTION_GRACE_MS) {
                lastBgTimestamp = newTimestamp
                return@LaunchedEffect
            }
            // Reset scroll+zoom to their defaults by recreating the state objects (see
            // bgViewportResetTrigger doc above) instead of driving VicoZoomState.zoom(Zoom) to an
            // absolute target, which was found to land on the wrong zoom/scroll combination.
            bgViewportResetTrigger++
        }
        lastBgTimestamp = newTimestamp
    }

    // After a reset, the recreated bgScrollState defaults to Scroll.Absolute.End. If predictions
    // are visible, nudge it further so "now + 2h" sits at the right edge instead, leaving room to
    // see the forecast — same positioning as before, reapplied once the fresh scrollState (a new
    // instance, since it's keyed on bgViewportResetTrigger too) is composed and ready.
    LaunchedEffect(bgViewportResetTrigger, bgScrollState) {
        if (bgViewportResetTrigger == 0) return@LaunchedEffect
        val showPredictions = SeriesType.PREDICTIONS in graphConfig.bgOverlays
        val timeRange = derivedTimeRange
        if (showPredictions && predictions.isNotEmpty() && timeRange != null) {
            val (minTimestamp, _) = timeRange
            val nowX = timestampToX(dateUtil.now(), minTimestamp)
            bgScrollState.animateScroll(Scroll.Absolute.x(nowX + 120.0, bias = 1f))
        }
    }

    // Correct secondary graph scroll drift — Vico may internally adjust scroll
    // when model producers fire. Watch for any divergence and re-sync to BG.
    // No isSyncing guard needed: primary sync only reads BG state, so writing to
    // secondary states here cannot trigger primary sync (no feedback loop).
    // Keyed on bgScrollState/bgZoomState (not Unit) — MUST restart when they're recreated by a
    // reset, otherwise this keeps comparing secondary graphs against a stale, abandoned pre-reset
    // reference forever and fires wrong corrections (the actual cause of the "dancing" regression
    // in the first attempt at this feature).
    LaunchedEffect(bgScrollState, bgZoomState) {
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
                    // No wait between zoom and scroll, see the sync above
                    beltScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    iobScrollState.scroll(Scroll.Absolute.pixels(bgScroll))
                    for (i in 0 until count) secScrollStates[i].scroll(Scroll.Absolute.pixels(bgScroll))
                }
            }
    }


    // Set by the BG graph, read by every graph
    val inspect = remember { GraphInspectState() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Treatment Belt Graph - running mode background + therapy events
        TreatmentBeltGraphCompose(
            viewModel = graphViewModel,
            inspect = inspect,
            scrollState = beltScrollState,
            zoomState = beltZoomState,
            derivedTimeRange = derivedTimeRange,
            nowTimestamp = nowTimestamp,
            modifier = Modifier.fillMaxWidth()
        )
        // BG Graph - primary interactive graph
        var editingBgOverlays by remember { mutableStateOf(false) }

        // The graph is drawn, so a screen reader gets nothing from it at all - a chart contributes
        // no semantics node of its own. Describe it instead: the recent readings newest first,
        // because the current value is what matters most and a listener may stop there, plus what
        // is on board. The description goes on the graph itself rather than the Box, so the edit
        // button beside it stays a separate control.
        val bgReadings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
        val profileUtil = LocalProfileUtil.current
        val decimalFormatter = LocalDecimalFormatter.current
        val recentValues = remember(bgReadings, profileUtil.units) {
            // BgDataPoint.value is ALREADY in the user's units, so it must not be converted again -
            // only formatted. The unit decides the decimals; the "detect" helpers guess that from
            // the magnitude and would read a very high mmol/l reading as mg/dl.
            newestReadings(bgReadings, RECENT_VALUES_SPOKEN)
                .joinToString(", ") {
                    if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(it.value)
                    else decimalFormatter.to1Decimal(it.value)
                }
        }
        val graphDescription = when {
            recentValues.isEmpty()                 -> null
            iobText != null && cobText != null     -> stringResource(
                UiStrings.a11y_bg_graph_summary,
                recentValues,
                stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.iob), iobText),
                stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.cob), cobText)
            )

            else                                   -> stringResource(UiStrings.a11y_bg_graph_summary_values_only, recentValues)
        }

        // The predicted curves are drawn and nothing else on the screen states them, so they were
        // out of reach entirely. Each curve gets one sentence naming where it is heading, which is
        // what a listener can actually hold in their head - reading a whole series of points back
        // would be unusable. Reported as the biggest excursion from the current glucose rather than
        // the final point, because a curve that dips to 3.2 before settling at 6.0 matters for the
        // dip, and that is the reading of "heading up to" or "down to" a user means.
        val currentValue = remember(bgReadings) { newestReadings(bgReadings, 1).firstOrNull()?.value }
        val unitLabel = if (profileUtil.units == GlucoseUnit.MMOL) stringResource(CoreUiStrings.mmol) else stringResource(CoreUiStrings.mgdl)
        // Nothing is said about curves the user has switched off the graph.
        val predictionsVisible = SeriesType.PREDICTIONS in graphConfig.bgOverlays
        val predictionExtremes = remember(predictions, currentValue, predictionsVisible) {
            if (!predictionsVisible || currentValue == null) emptyList()
            else PREDICTION_SPOKEN_ORDER.mapNotNull { (type, name) ->
                predictionExtreme(predictions.filter { it.type == type }.map { it.value }, currentValue)
                    ?.let { (value, direction) -> Triple(name, value, direction) }
            }
        }
        // map, not joinToString: the latter is not inline, so a @Composable call cannot sit in its
        // lambda. The finished sentences are joined afterwards, as the graph summary joins its values.
        val predictionSentences = predictionExtremes.map { (name, value, direction) ->
            // BgDataPoint.value is ALREADY in the user's units, as above: format, never convert.
            val shown = stringResource(
                CoreUiStrings.value_with_unit,
                if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(value) else decimalFormatter.to1Decimal(value),
                unitLabel
            )
            when (direction) {
                PredictionDirection.UP   -> stringResource(UiStrings.a11y_prediction_rises, stringResource(name), shown)
                PredictionDirection.DOWN -> stringResource(UiStrings.a11y_prediction_falls, stringResource(name), shown)
                PredictionDirection.FLAT -> stringResource(UiStrings.a11y_prediction_flat, stringResource(name), shown)
            }
        }
        val predictionDescription =
            if (predictionSentences.isEmpty()) null
            else stringResource(UiStrings.a11y_predictions, predictionSentences.joinToString(" "))

        Box(
            modifier = Modifier
                .offset(y = (-16).dp)
                .graphInspectTouch(inspect)
        ) {
            BgGraphCompose(
                viewModel = graphViewModel,
                bgOverlays = graphConfig.bgOverlays,
                scrollState = bgScrollState,
                zoomState = bgZoomState,
                derivedTimeRange = derivedTimeRange,
                nowTimestamp = nowTimestamp,
                visibleTimeRange = bgVisibleTimeRange,
                inspect = inspect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(graphConfig.bgHeight.dp)
                    .onScrollOrZoomGesture(graphViewModel::onGraphInteraction)
                    .then(
                        // Merging, not a bare semantics block: a described node is reached by linear
                        // screen-reader navigation only when it is a leaf or a merging parent. This one
                        // has a child (the chart's own node, which carries no text), so unmerged it was
                        // skipped in both directions and could be reached only as the first step from
                        // the glucose circle or at the wrap point after the last tab - measured with
                        // TalkBack on a phone. Merging absorbs that empty child, so the words do not
                        // change and no action comes with it; the edit button is a clickable sibling and
                        // stays its own stop.
                        if (graphDescription != null) Modifier.semantics(mergeDescendants = true) { contentDescription = graphDescription }
                        else Modifier
                    )
            )
            if (!isSimpleMode) {
                GraphEditButton(
                    onClick = { editingBgOverlays = true },
                    graphName = stringResource(CoreUiStrings.glucose),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 2.dp)
                )
            }
            // Own composable, so a finger move does not recompose this section
            GraphInspectOverlay(
                inspect = inspect,
                graphViewModel = graphViewModel,
                bgReadings = bgReadings,
                minTimestamp = derivedTimeRange?.first,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = INSPECT_PILL_OFFSET)
                    .padding(top = 2.dp, start = 36.dp, end = 36.dp)
            )
        }
        // A node of its own rather than more text on the graph above, so a listener reaches the
        // readings first and swipes once to decide whether she wants the forecast, instead of having
        // to sit through it every time. It needs real bounds to be reachable at all, hence a 1 dp
        // strip: invisible on screen, present in the accessibility tree.
        predictionDescription?.let { description ->
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .semantics { contentDescription = description }
            )
        }
        if (editingBgOverlays) {
            GraphSeriesBottomSheet(
                title = stringResource(CoreUiStrings.graph_bg),
                selectedSeries = graphConfig.bgOverlays,
                availableSeries = BG_OVERLAY_SERIES,
                height = graphConfig.bgHeight,
                onHeightChange = { h ->
                    graphViewModel.updateGraphConfig(graphConfig.copy(bgHeight = h))
                },
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
        Box(modifier = Modifier.pullUp(IOB_GRAPH_PULL_UP)) {
            SecondaryGraphCompose(
                viewModel = graphViewModel,
                seriesTypes = listOf(SeriesType.IOB),
                scrollState = iobScrollState,
                zoomState = iobZoomState,
                derivedTimeRange = derivedTimeRange,
                nowTimestamp = nowTimestamp,
                activityOverlay = SeriesType.ACTIVITY in graphConfig.iobOverlays,
                onVisibleRangeChanged = { iobVisibleRange = it },
                inspect = inspect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(graphConfig.iobHeight.dp - REMOVED_TIME_LABELS_HEIGHT)
            )
            GraphNameRow(
                name = stringResource(CoreUiStrings.iob) + " / " + stringResource(CoreUiStrings.basal_shortname),
                editName = stringResource(CoreUiStrings.iob),
                onEdit = if (isSimpleMode) null else ({ editingIobOverlays = true }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = GRAPH_NAME_BOTTOM_PADDING)
            )
        }
        if (editingIobOverlays) {
            GraphSeriesBottomSheet(
                title = stringResource(CoreUiStrings.iob) + " / " + stringResource(CoreUiStrings.basal_shortname),
                selectedSeries = graphConfig.iobOverlays,
                availableSeries = listOf(SeriesType.ACTIVITY),
                height = graphConfig.iobHeight,
                onHeightChange = { h ->
                    graphViewModel.updateGraphConfig(graphConfig.copy(iobHeight = h))
                },
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
            val secondary = graphConfig.secondaryGraphs[i]
            Box(modifier = Modifier.pullUp(GRAPH_OVERLAP)) {
                SecondaryGraphCompose(
                    viewModel = graphViewModel,
                    inspect = inspect,
                    seriesTypes = secondary.series,
                    scrollState = secScrollStates[i],
                    zoomState = secZoomStates[i],
                    derivedTimeRange = derivedTimeRange,
                    nowTimestamp = nowTimestamp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(secondary.height.dp - REMOVED_TIME_LABELS_HEIGHT)
                )
                GraphNameRow(
                    name = seriesListLabel(secondary.series),
                    editName = seriesListLabel(secondary.series),
                    onEdit = if (isSimpleMode) null else ({ editingGraphIndex = i }),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = GRAPH_NAME_BOTTOM_PADDING)
                )
            }
        }
        if (editingGraphIndex >= 0 && editingGraphIndex < activeCount) {
            // Read once. The lambdas below can run after the sheet has closed and the index is
            // already -1: NumberInputRow saves a focused field when it leaves the screen.
            val index = editingGraphIndex
            val editing = graphConfig.secondaryGraphs[index]
            GraphSeriesBottomSheet(
                title = stringResource(CoreUiStrings.graph_number, index + 2),
                selectedSeries = editing.series,
                availableSeries = CONFIGURABLE_SERIES,
                height = editing.height,
                onHeightChange = { h ->
                    // The current config, not the captured one: after "Remove graph" the captured
                    // config still holds the removed graph, and writing it back would restore it.
                    withSecondaryGraphHeight(graphViewModel.graphConfigFlow.value, index, editing.series, h)
                        ?.let { graphViewModel.updateGraphConfig(it) }
                },
                onToggle = { type ->
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    val current = graphs[index].series.toMutableList()
                    if (type in current) {
                        current.remove(type)
                    } else {
                        current.add(type)
                        if (current.size > 2) current.removeAt(0) // FIFO: drop oldest
                    }
                    if (current.isEmpty()) {
                        // Auto-remove graph when all series deselected
                        graphs.removeAt(index)
                        editingGraphIndex = -1
                    } else {
                        graphs[index] = graphs[index].copy(series = current)
                    }
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onRemoveGraph = {
                    val graphs = graphConfig.secondaryGraphs.toMutableList()
                    graphs.removeAt(index)
                    editingGraphIndex = -1
                    graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                },
                onDismiss = { editingGraphIndex = -1 }
            )
        }
        // Add graph button. In simple mode, or with all graphs added, it stays as an invisible placeholder: its height
        // is part of the space below the last graph, and the spacer alone is lower than the QuickLaunch toolbar.
        val canAddGraph = !isSimpleMode && activeCount < GraphConfig.MAX_SECONDARY_GRAPHS
        var showAddSheet by remember { mutableStateOf(false) }
        TextButton(
            onClick = { showAddSheet = true },
            enabled = canAddGraph,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canAddGraph) Modifier else Modifier.alpha(0f).clearAndSetSemantics {})
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(CoreUiStrings.graph_add), style = MaterialTheme.typography.labelMedium)
        }
        if (canAddGraph && showAddSheet) {
            var newGraphSeries by remember { mutableStateOf(emptyList<SeriesType>()) }
            var newGraphHeight by remember { mutableIntStateOf(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP) }
            GraphSeriesBottomSheet(
                title = stringResource(CoreUiStrings.graph_new),
                selectedSeries = newGraphSeries,
                availableSeries = CONFIGURABLE_SERIES,
                height = newGraphHeight,
                onHeightChange = { newGraphHeight = it },
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
                        graphs.add(SecondaryGraph(newGraphSeries, newGraphHeight))
                        graphViewModel.updateGraphConfig(graphConfig.copy(secondaryGraphs = graphs))
                    }
                    newGraphSeries = emptyList()
                    newGraphHeight = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                    showAddSheet = false
                }
            )
        }
        // Spacer so the last graph / Add button isn't covered by QuickLaunch toolbar
        Spacer(Modifier.height(48.dp))
    }
}

/**
 * The [count] newest readings, newest first.
 *
 * Sorted here rather than trusted: the graph data comes newest first, and reading it as oldest
 * first made the screen reader speak the readings from 24 hours ago instead of the current ones.
 */
internal fun newestReadings(readings: List<BgDataPoint>, count: Int): List<BgDataPoint> =
    readings.sortedByDescending { it.timestamp }.take(count)

/**
 * [config] with the height of secondary graph [index] set to [height], or null when that graph no
 * longer shows [expectedSeries].
 *
 * The height field saves its text when the settings sheet closes, so this can run after the graph
 * was removed. Then [index] points to another graph, or to nothing, and nothing may be written.
 * Graphs have no id, so the graph is recognised by its series. The height is not compared: quick
 * taps on the +/- buttons can arrive before the screen has caught up with the previous one.
 * With two graphs showing the same series, the other one could get the height. That is harmless.
 */
internal fun withSecondaryGraphHeight(config: GraphConfig, index: Int, expectedSeries: List<SeriesType>, height: Int): GraphConfig? {
    val graph = config.secondaryGraphs.getOrNull(index) ?: return null
    if (graph.series != expectedSeries) return null
    val graphs = config.secondaryGraphs.toMutableList()
    graphs[index] = graph.copy(height = height)
    return config.copy(secondaryGraphs = graphs)
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
private fun seriesShortNameId(type: SeriesType): TextRef = when (type) {
    SeriesType.IOB             -> CoreUiStrings.iob
    SeriesType.ABS_IOB         -> CoreUiStrings.abs_insulin_shortname
    SeriesType.COB             -> CoreUiStrings.cob
    SeriesType.BGI             -> CoreUiStrings.bgi_shortname
    SeriesType.DEVIATIONS      -> CoreUiStrings.deviation_shortname
    SeriesType.SENSITIVITY     -> CoreUiStrings.sensitivity_shortname
    SeriesType.VAR_SENSITIVITY -> CoreUiStrings.variable_sensitivity_shortname
    SeriesType.DEV_SLOPE       -> CoreUiStrings.devslope_shortname
    SeriesType.HEART_RATE      -> CoreUiStrings.heartRate_shortname
    SeriesType.STEPS           -> CoreUiStrings.steps_shortname
    SeriesType.ACTIVITY        -> CoreUiStrings.activity_shortname
    SeriesType.PREDICTIONS     -> CoreUiStrings.predictions_shortname
}

// =========================================================================
// Graph edit button + series picker bottom sheet
// =========================================================================

/**
 * Name of a graph below BG, with its edit button (pencil) to the right of it. [onEdit] is null in simple mode,
 * where graphs cannot be edited. The row is at least as tall as the edit button, also without it, so the name
 * stays at the same height in both modes.
 */
@Composable
private fun GraphNameRow(
    name: String,
    editName: String,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = GRAPH_EDIT_BUTTON_SIZE)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (onEdit != null) GraphEditButton(onClick = onEdit, graphName = editName)
    }
}

/** Small pencil icon button overlaid on a graph */
@Composable
private fun GraphEditButton(
    onClick: () -> Unit,
    graphName: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(GRAPH_EDIT_BUTTON_SIZE),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            // One per graph, so it names its graph: "Edit: COB", not "Edit" four times.
            contentDescription = rowAction(CoreUiStrings.switch_to_edit, graphName),
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
    height: Int,
    onHeightChange: (Int) -> Unit,
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
                        Text(stringResource(CoreUiStrings.remove))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            NumberInputRow(
                labelRef = CoreUiStrings.graph_height,
                value = height.toDouble(),
                onValueChange = { onHeightChange(it.toInt()) },
                valueRange = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP.toDouble()..GraphConfig.MAX_GRAPH_HEIGHT_DP.toDouble(),
                step = 10.0,
                formatAsInt = true
            )
            Spacer(Modifier.height(8.dp))
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
