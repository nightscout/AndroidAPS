package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.TonalIcon
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import kotlin.math.abs
import kotlin.math.roundToInt

// =========================================================================
// Press and hold on the BG graph: a line and a dot on the reading under the finger, and a pill with
// the values at that time. Inspired by Trio and TrioFollower.
// =========================================================================

/** How far a reading or a value may be from the finger's time and still count, in ms. */
private const val INSPECT_MATCH_MS = 5 * 60_000L

/** A bolus or carb entry belongs to a reading when it is within half a reading interval of it, in ms. */
private const val INSPECT_TREATMENT_MS = 150_000L

/** The values that can be shown in the pill. Time and glucose are always shown. [id] is what is stored. */
enum class InspectItem(val id: String, val label: TextRef) {

    IOB("iob", CoreUiStrings.iob),
    COB("cob", CoreUiStrings.cob),
    BASAL("basal", CoreUiStrings.basal),
    TREATMENTS("treatments", UiStrings.graph_inspect_treatments);

    companion object {

        /** Stored form: the ids of the hidden items, separated by commas. Empty = everything shown. */
        fun hiddenFromString(text: String): Set<InspectItem> =
            text.split(",").mapNotNull { id -> entries.find { it.id == id.trim() } }.toSet()

        fun hiddenToString(hidden: Set<InspectItem>): String = entries.filter { it in hidden }.joinToString(",") { it.id }
    }
}

/**
 * Shared state between Vico (marker, controller, listener) and the compose side (pill, touch layer).
 * Vico reports where the finger is ([targetX]); the compose side snaps it to a reading and tells the
 * marker where to draw ([shownX], [dotValue], [dotColor]).
 */
@Stable
class GraphInspectState {

    /** True from the long press until the finger is lifted. */
    var fingerDown by mutableStateOf(false)

    /** The graph x (minutes from the start of the graph) under the finger, from Vico. */
    var targetX by mutableStateOf<Double?>(null)

    /** The graph x of the reading the line and dot are drawn at. */
    var shownX by mutableStateOf<Double?>(null)
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

        override fun onHidden(marker: CartesianMarker) {
            fingerDown = false
        }
    }
}

/**
 * Draws a vertical line through the graph at the chosen reading, and a dot on the reading.
 * Vico places the marker at its own nearest point (which may be a basal or prediction point), so the
 * position is moved to the reading the pill shows.
 */
class GraphInspectMarker(
    private val state: GraphInspectState,
    lineColor: Color
) : CartesianMarker {

    private val line = LineComponent(fill = Fill(lineColor), thickness = 1.dp)
    private val dotCenter = ShapeComponent(fill = Fill(Color.White), shape = CircleShape)

    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        val target = targets.firstOrNull() ?: return
        val x = state.shownX ?: return
        with(context) {
            val canvasX = target.canvasX + ((x - target.x) / ranges.xStep).toFloat() * layerDimensions.xSpacing
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return
            line.drawVertical(context, canvasX, layerBounds.top, layerBounds.bottom)
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
 * While inspecting, eats finger moves before the graph sees them, so the graph does not scroll and
 * only the marker follows the finger. Vico still gets the moves for the marker: its interaction
 * handler does not skip consumed events, the scrolling does.
 */
fun Modifier.graphInspectTouch(state: GraphInspectState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (state.fingerDown && event.type == PointerEventType.Move) event.changes.forEach { it.consume() }
        }
    }
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
 * The pill with the values at the inspected time. [onEdit] opens the switches for what is shown.
 */
@Composable
fun GraphInspectPill(
    data: InspectData,
    hidden: Set<InspectItem>,
    onEdit: () -> Unit,
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
            modifier = Modifier.padding(start = AapsSpacing.large, end = AapsSpacing.extraSmall)
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
                if (InspectItem.IOB !in hidden) data.iob?.let {
                    PillItem(ElementType.INSULIN.icon(), stringResource(InterfacesStrings.format_insulin_units, it), ElementType.INSULIN.color())
                }
                if (InspectItem.COB !in hidden) data.cob?.let {
                    PillItem(ElementType.COB.icon(), stringResource(InterfacesStrings.format_carbs, it.roundToInt()), ElementType.COB.color())
                }
                if (InspectItem.BASAL !in hidden) data.basal?.let {
                    PillItem(ElementType.TEMP_BASAL.icon(), stringResource(CoreUiStrings.pump_base_basal_rate, it), ElementType.TEMP_BASAL.color())
                }
                if (InspectItem.TREATMENTS !in hidden) {
                    data.boluses.forEach { bolus ->
                        val text = if (bolus.bolusType == BolusType.SMB) stringResource(UiStrings.graph_inspect_smb, bolus.label) else bolus.label
                        PillItem(IcBolus, text, ElementType.INSULIN.color())
                    }
                    data.carbs.forEach { carbs -> PillItem(IcCarbs, carbs.label, ElementType.CARBS.color()) }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(CoreUiStrings.switch_to_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PillText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun PillItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        PillText(text, color)
    }
}

/** Bottom sheet with one switch per value that the pill can show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphInspectItemsSheet(
    hidden: Set<InspectItem>,
    onChange: (Set<InspectItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = AapsSpacing.xxLarge)) {
            Text(
                text = stringResource(UiStrings.graph_inspect_items_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AapsSpacing.xxLarge, vertical = AapsSpacing.large)
            )
            InspectItem.entries.forEach { item ->
                val type = when (item) {
                    InspectItem.IOB        -> ElementType.INSULIN
                    InspectItem.COB        -> ElementType.COB
                    InspectItem.BASAL      -> ElementType.TEMP_BASAL
                    InspectItem.TREATMENTS -> ElementType.TREATMENT
                }
                ListItem(
                    headlineContent = { Text(stringResource(item.label)) },
                    leadingContent = { TonalIcon(icon = type.icon(), color = type.color()) },
                    trailingContent = {
                        Switch(
                            checked = item !in hidden,
                            onCheckedChange = { shown -> onChange(if (shown) hidden - item else hidden + item) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}
