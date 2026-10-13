package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// =========================================================================
// Press and hold on the BG graph: a line and a dot on the reading under the finger, and a pill with
// the values at that time; a dashed line at that time in every graph. Inspired by Trio and TrioFollower.
// =========================================================================

/** How far a reading or a value may be from the finger's time and still count, in ms. */
private const val INSPECT_MATCH_MS = 5 * 60_000L

/** A bolus or carb entry belongs to a reading when it is within half a reading interval of it, in ms. */
private const val INSPECT_TREATMENT_MS = 150_000L

/** How long the pill stays after the finger is lifted. */
private const val INSPECT_LINGER_MS = 2_000L

/** Size of the icons in the pill. */
private val PILL_ICON_SIZE = 14.dp

/** The COB icon is drawn smaller, see [GraphInspectPill]. */
private val PILL_COB_ICON_SIZE = 12.dp

/**
 * Shared state between Vico (marker, controller, listener) and the compose side (pill, touch layer).
 * Vico reports where the finger is ([targetX]); the compose side snaps it to a reading and tells the
 * marker where to draw ([shownX], [dotValue], [dotColor]).
 */
@Stable
class GraphInspectState {

    /** True from the long press until the finger is lifted. */
    var fingerDown by mutableStateOf(false)

    /** A finger is on the BG graph, from the touch layer. Plain field: only read in callbacks. */
    var pointerDown = false

    /** The graph x (minutes from the start of the graph) under the finger, from Vico. */
    var targetX by mutableStateOf<Double?>(null)

    /** The graph x of the reading the line and dot are drawn at. */
    var shownX by mutableStateOf<Double?>(null)

    /** Where the inspect line is drawn in every graph: at the reading, while the finger is down. */
    val lineX: Double? get() = if (fingerDown) shownX else null
    var dotValue by mutableStateOf<Double?>(null)
    var dotColor by mutableStateOf(Color.Unspecified)

    val controller: CartesianMarkerController = object : CartesianMarkerController {
        override val acceptsLongPress: Boolean = true

        // Only a long press starts it; after that it follows the finger until it is lifted or a zoom starts
        override fun shouldAcceptInteraction(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean =
            when (interaction) {
                is Interaction.LongPress                                          -> true
                is Interaction.Move, is Interaction.Release, is Interaction.Zoom -> fingerDown
                else                                                              -> false
            }

        override fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean {
            val show = interaction is Interaction.LongPress || interaction is Interaction.Move
            fingerDown = show
            return show
        }
    }

    val visibilityListener: CartesianMarkerVisibilityListener = object : CartesianMarkerVisibilityListener {
        override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
            targetX = targets.firstOrNull()?.x
        }

        override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
            targetX = targets.firstOrNull()?.x
        }

        // Vico also hides the marker when the finger leaves the plotted area (no reading there), with
        // the finger still down. Keep inspecting then: the pill stays, and the line follows again on return.
        override fun onHidden(marker: CartesianMarker) {
            if (!pointerDown) fingerDown = false
        }
    }
}

/**
 * Draws a dot on the chosen reading (the line through all graphs is [InspectLine]).
 * Vico places the marker at its own nearest point (which may be a basal or prediction point), so the
 * position is moved to the reading the pill shows.
 */
class GraphInspectMarker(
    private val state: GraphInspectState
) : CartesianMarker {

    private val dotCenter = ShapeComponent(fill = Fill(Color.White), shape = CircleShape)

    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        // Only while the finger is down, like the line
        if (!state.fingerDown) return
        val target = targets.firstOrNull() ?: return
        val x = state.shownX ?: return
        with(context) {
            val canvasX = target.canvasX + ((x - target.x) / ranges.xStep).toFloat() * layerDimensions.xSpacing
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return
            val value = state.dotValue ?: return
            val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
            val canvasY = layerBounds.bottom - ((value - yRange.minY) / yRange.length).toFloat() * layerBounds.height
            val outer = 7.dp.pixels
            val inner = 3.dp.pixels
            ShapeComponent(fill = Fill(state.dotColor), shape = CircleShape)
                .draw(context, canvasX - outer, canvasY - outer, canvasX + outer, canvasY + outer)
            dotCenter.draw(context, canvasX - inner, canvasY - inner, canvasX + inner, canvasY + inner)
        }
    }
}

/**
 * The vertical line at the inspected reading, drawn in every graph so they all point at the same time.
 * Drawn like [NowLine] (dashed, same thickness), and moved with the finger through [GraphInspectState.lineX].
 */
class InspectLine(
    private val state: GraphInspectState,
    private val color: Color,
    private val strokeWidthPx: Float = 2f,
    private val dashLengthPx: Float = 6f,
    private val gapLengthPx: Float = 4f
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val x = state.lineX ?: return
        with(context) {
            val xStep = ranges.xStep
            if (xStep == 0.0) return
            // Same x to canvas transform as NowLine
            val canvasX = layerBounds.left +
                layerDimensions.startPadding +
                layerDimensions.xSpacing * ((x - ranges.minX) / xStep).toFloat() -
                scroll
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return
            with(mutableDrawScope) {
                drawLine(
                    color = this@InspectLine.color,
                    start = Offset(canvasX, layerBounds.top),
                    end = Offset(canvasX, layerBounds.bottom),
                    strokeWidth = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, gapLengthPx), 0f)
                )
            }
        }
    }
}

/** The [InspectLine] for one graph, or null when [state] is null. Colored like the "now" line, a bit lighter. */
@Composable
fun rememberInspectLine(state: GraphInspectState?): InspectLine? {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    return remember(state, color) { state?.let { InspectLine(it, color) } }
}

/**
 * While inspecting, eats finger moves before the graph sees them, so the graph does not scroll and
 * only the marker follows the finger. Vico still gets the moves for the marker: its interaction
 * handler does not skip consumed events, the scrolling does.
 * Also tracks whether a finger is down, so only lifting it ends inspecting (see onHidden).
 * Inspecting is ended in the Final pass, after Vico: Vico only hides the marker on the lift when it
 * still sees inspecting as on. Ending it earlier left the marker dot on the graph.
 */
fun Modifier.graphInspectTouch(state: GraphInspectState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            state.pointerDown = event.changes.any { it.pressed }
            if (state.fingerDown && event.type == PointerEventType.Move) event.changes.forEach { it.consume() }
            // The same event again, after Vico had it. A safety net when Vico did not get the lift.
            awaitPointerEvent(PointerEventPass.Final)
            if (!state.pointerDown) state.fingerDown = false
        }
    }
}

/**
 * The compose side of press and hold: snaps the finger to a reading, gives haptic feedback, and shows
 * the pill until [INSPECT_LINGER_MS] after the finger is lifted. The finger is followed with
 * snapshotFlow, not effect keys, so a move recomposes only this, and only when the reading changes.
 */
@Composable
fun GraphInspectOverlay(
    inspect: GraphInspectState,
    graphViewModel: GraphViewModel,
    bgReadings: List<BgDataPoint>,
    minTimestamp: Long?,
    modifier: Modifier = Modifier
) {
    var inspectData by remember { mutableStateOf<InspectData?>(null) }
    val iobGraph by graphViewModel.iobGraphFlow.collectAsStateWithLifecycle()
    val cobGraph by graphViewModel.cobGraphFlow.collectAsStateWithLifecycle()
    val basalGraph by graphViewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val treatmentGraph by graphViewModel.treatmentGraphFlow.collectAsStateWithLifecycle()
    val currentBgReadings by rememberUpdatedState(bgReadings)
    val currentMinTimestamp by rememberUpdatedState(minTimestamp)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(inspect, haptic) {
        snapshotFlow { inspect.fingerDown to inspect.targetX }.collect { (fingerDown, x) ->
            val min = currentMinTimestamp
            if (!fingerDown || x == null || min == null) return@collect
            val data = inspectDataAt(
                timestamp = min + (x * 60_000).toLong(),
                bgReadings = currentBgReadings,
                iob = iobGraph.iob,
                cob = cobGraph.cob,
                actualBasal = basalGraph.actualBasal,
                boluses = treatmentGraph.boluses,
                carbs = treatmentGraph.carbs
            )
            if (data.timestamp != inspectData?.timestamp) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            inspect.shownX = timestampToX(data.timestamp, min)
            inspect.dotValue = data.bg?.value
            inspectData = data
        }
    }
    LaunchedEffect(inspect, haptic) {
        snapshotFlow { inspect.fingerDown }.collect { if (it) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    LaunchedEffect(inspect) {
        snapshotFlow { inspect.fingerDown to inspectData }.collectLatest { (fingerDown, data) ->
            if (!fingerDown && data != null) {
                delay(INSPECT_LINGER_MS)
                inspectData = null
            }
        }
    }

    val data = inspectData
    val dotColor = data?.bg?.range?.inspectColor() ?: Color.Unspecified
    SideEffect { inspect.dotColor = dotColor }
    if (data != null) GraphInspectPill(data = data, modifier = modifier)
}

/** The values at one reading time, for the pill. Null values have no data near that time. */
data class InspectData(
    val timestamp: Long,
    val bg: BgDataPoint?,
    val iob: Double?,
    val cob: Double?,
    val basal: Double?,
    val boluses: List<BolusGraphPoint>,
    val carbs: List<CarbsGraphPoint>
)

/**
 * Finds what to show for [timestamp]: the nearest reading (within 5 min), and the IOB, COB and basal
 * at that reading's time, plus boluses and carbs given within half a reading interval of it.
 */
fun inspectDataAt(
    timestamp: Long,
    bgReadings: List<BgDataPoint>,
    iob: List<GraphDataPoint>,
    cob: List<GraphDataPoint>,
    actualBasal: List<GraphDataPoint>,
    boluses: List<BolusGraphPoint>,
    carbs: List<CarbsGraphPoint>
): InspectData {
    val bg = bgReadings.nearest(timestamp) { it.timestamp }
    val time = bg?.timestamp ?: timestamp
    return InspectData(
        timestamp = time,
        bg = bg,
        iob = iob.nearest(time) { it.timestamp }?.value,
        cob = cob.nearest(time) { it.timestamp }?.value,
        // Basal is stored at each change, so the rate at a time is the last change before it
        basal = actualBasal.lastOrNull { it.timestamp <= time }?.value,
        boluses = boluses.filter { it.isValid && abs(it.timestamp - time) <= INSPECT_TREATMENT_MS },
        carbs = carbs.filter { it.isValid && abs(it.timestamp - time) <= INSPECT_TREATMENT_MS }
    )
}

/** COB in whole grams for the pill, or null to leave it out: no carbs on board is not worth a place. */
internal fun shownCob(cob: Double?): Int? = cob?.roundToInt()?.takeIf { it > 0 }

private fun <T> List<T>.nearest(timestamp: Long, time: (T) -> Long): T? =
    minByOrNull { abs(time(it) - timestamp) }?.takeIf { abs(time(it) - timestamp) <= INSPECT_MATCH_MS }

/** BG range to the BG color of the graph. */
@Composable
internal fun BgRange.inspectColor(): Color = when (this) {
    BgRange.HIGH     -> AapsTheme.generalColors.bgHigh
    BgRange.IN_RANGE -> AapsTheme.generalColors.bgInRange
    BgRange.LOW      -> AapsTheme.generalColors.bgLow
}

/**
 * The pill with the values at the inspected time: time, glucose, IOB, COB (left out at 0 g), basal
 * rate, and any bolus, SMB or carbs at that reading.
 */
@Composable
fun GraphInspectPill(
    data: InspectData,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    val profileUtil = LocalProfileUtil.current
    val decimalFormatter = LocalDecimalFormatter.current
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AapsSpacing.large)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall),
                modifier = Modifier.padding(vertical = AapsSpacing.small)
            ) {
                PillText(dateUtil.timeString(data.timestamp), MaterialTheme.colorScheme.onSurfaceVariant)
                data.bg?.let { bg ->
                    val text = if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(bg.value) else decimalFormatter.to1Decimal(bg.value)
                    PillText(text, bg.range.inspectColor())
                }
                data.iob?.let {
                    PillItem(ElementType.INSULIN.icon(), stringResource(InterfacesStrings.format_insulin_units, it), ElementType.INSULIN.color())
                }
                shownCob(data.cob)?.let {
                    // The carbs icon fills more of its box than the insulin icon, so it is drawn smaller to look the same size
                    PillItem(ElementType.COB.icon(), stringResource(InterfacesStrings.format_carbs, it), ElementType.COB.color(), iconSize = PILL_COB_ICON_SIZE)
                }
                data.basal?.let {
                    PillItem(ElementType.TEMP_BASAL.icon(), stringResource(CoreUiStrings.pump_base_basal_rate, it), ElementType.TEMP_BASAL.color())
                }
                // Treatments use the triangles of the IOB and COB graphs: SMB points up, bolus and carbs point down
                data.boluses.forEach { bolus ->
                    PillTriangleItem(pointsUp = bolus.bolusType == BolusType.SMB, text = bolus.label, color = ElementType.INSULIN.color())
                }
                data.carbs.forEach { carbs -> PillTriangleItem(pointsUp = false, text = carbs.label, color = ElementType.CARBS.color()) }
            }
        }
    }
}

@Composable
private fun PillText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun PillItem(icon: ImageVector, text: String, color: Color, iconSize: Dp = PILL_ICON_SIZE) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PILL_ICON_SIZE)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        }
        PillText(text, color)
    }
}

@Composable
private fun PillTriangleItem(pointsUp: Boolean, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
        PillTriangle(pointsUp = pointsUp, color = color)
        PillText(text, color)
    }
}

/**
 * A treatment triangle like in the IOB and COB graphs, with the same proportions as [TriangleShape]
 * and [InvertedTriangleShape]. Those shapes only fill the top half of their box, so the triangle is
 * drawn here, centered in the icon box.
 */
@Composable
private fun PillTriangle(pointsUp: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(PILL_ICON_SIZE)) {
        // Same as the graph shapes: base 0.6 of the width, height 0.5 of the width
        val baseHalf = size.width * 0.3f
        val height = size.width * 0.5f
        val cx = size.width / 2f
        val top = (size.height - height) / 2f
        val bottom = top + height
        val path = Path().apply {
            if (pointsUp) {
                moveTo(cx, top)
                lineTo(cx + baseHalf, bottom)
                lineTo(cx - baseHalf, bottom)
            } else {
                moveTo(cx - baseHalf, top)
                lineTo(cx + baseHalf, top)
                lineTo(cx, bottom)
            }
            close()
        }
        drawPath(path, color)
    }
}
