package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.endOf
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.navigation.ElementColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.DrawingContext
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.coroutines.delay

/** How near to an icon a tap has to be to show that icon's event: half the icon and a little more. */
private val BELT_ICON_TAP_RADIUS = 12.dp


/**
 * The COB failover stretches of a model, for [CobFailoverBar]. Carried in the model, so the bar always
 * belongs to the model that is on screen, never a newer one that Vico has not applied yet.
 */
private val COB_FAILOVER_KEY = ExtraStore.Key<List<ClosedFloatingPointRange<Double>>>()

/** The events of a model that have one moment, no duration, as (x, type), for [BeltIcons]. */
private val BELT_ICONS_KEY = ExtraStore.Key<List<Pair<Double, TherapyEventType>>>()

/** Size of an event icon on the belt. */
private val BELT_ICON_SIZE = 16.dp

/** Size of the number on an icon that stands for more events. */
private val BELT_ICON_COUNT_FONT_SIZE = 10.sp

/**
 * Treatment Belt Graph — thin graph above BG graph showing:
 * - Running mode background colors (colored rectangles per mode segment)
 * - Therapy event icons (Announcement, Exercise, notes, etc.)
 * - COB failover: a thin bar along the bottom where carbs absorption was guessed, see [CobFailoverBar]
 *
 * An event that carries a glucose value (a meter value, a BG check) is NOT here: it is drawn inside
 * the BG graph at its own value, where it can be compared with the sensor curve. Such an event
 * without a value — not something the careportal can create — is then not drawn at all.
 *
 * What an icon or a colour means is shown in the inspect pill, see [GraphInspectState.beltController]:
 * press and hold as on every graph, or tap. The pill is drawn over the belt and goes away by itself.
 *
 * Architecture:
 * - Fixed Y range (0.0 to 1.0) — all events plotted at Y=0.5
 * - Running mode segments: 2-point area-fill series per segment (Y=1.0, full height)
 * - Therapy events with one moment: icons drawn by [BeltIcons], which joins icons that would overlap
 * - Duration events: 2-point line series per event
 * - No axis labels (clean belt appearance)
 * - Same 3 pillars as other graphs: fixed X range, getXStep = { _, _, _ -> 1.0 }, normalizer line
 *
 * Dynamic series order (must match lines list):
 *   1. Running mode segments (0..N, each = 2-point at Y=1.0 with area fill)
 *   2. Exercise duration lines (0..M, each = 2-point at Y=1.0, top)
 *   3. General with duration lines (0..K, each = 2-point at Y=1.0, top)
 *   4. Normalizer (always last)
 */
@Composable
fun TreatmentBeltGraphCompose(
    viewModel: GraphViewModel,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    /** The line at the inspected time, and a hold or a tap on the belt shows the pill. Null turns both off. */
    inspect: GraphInspectState? = null,
    /**
     * The visible x window of this graph, in minutes from the start of the axis. The belt follows the
     * scroll and zoom of BG and is always shown, so the BG graph takes its own window from here: a
     * decoration on BG's own chart broke its pinch-zoom.
     */
    onVisibleRangeChanged: ((Pair<Double, Double>?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    // Collect flows independently
    val runningModeData by viewModel.runningModeGraphFlow.collectAsStateWithLifecycle()
    val treatmentGraphData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()
    val cobData by viewModel.cobGraphFlow.collectAsStateWithLifecycle()

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = dateUtil.now()
        val dayAgo = now - Constants.GRAPH_TIME_RANGE_HOURS * 60 * 60 * 1000L
        dayAgo to now
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    // Colors from theme. The event icons are one neutral grey: the icon shape tells the event, and the
    // belt stays quiet above the BG graph. The inspect pill still shows each event in its own colour
    val elementColors = AapsTheme.elementColors
    val eventIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val careportalColor = eventIconColor
    val exerciseColor = eventIconColor

    // Icon painters for therapy events
    val announcementPainter = rememberVectorPainter(TherapyEventType.ANNOUNCEMENT.beltIcon())
    val activityPainter = rememberVectorPainter(TherapyEventType.EXERCISE.beltIcon())
    val notePainter = rememberVectorPainter(TherapyEventType.GENERAL.beltIcon())
    val clinicalNotesPainter = rememberVectorPainter(TherapyEventType.SETTINGS_EXPORT.beltIcon())

    val minX = 0.0
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }

    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentGraphData) }
    if (treatmentGraphData.therapyEvents.isNotEmpty()) {
        lastTreatmentData.value = treatmentGraphData
    }

    // Track which series are present (for dynamic line matching)
    val renderedModeSegmentsState = remember { mutableStateOf<List<Triple<Double, Double, RM.Mode>>>(emptyList()) }
    val modeSegmentCountState = remember { mutableIntStateOf(0) }
    val exerciseDurationCountState = remember { mutableIntStateOf(0) }
    val generalDurationCountState = remember { mutableIntStateOf(0) }

    // Cache last non-empty running mode data to survive reset() cycles
    val lastRunningModeData = remember { mutableStateOf(runningModeData) }
    if (runningModeData.segments.isNotEmpty()) {
        lastRunningModeData.value = runningModeData
    }

    LaunchedEffect(runningModeData, treatmentGraphData, cobData, stableTimeRange) {
        // Don't skip when hasRealTimeRange is false — always populate model to avoid flicker.
        // Use fallback time range so the chart skeleton (axes/border) always renders.
        if (!hasRealTimeRange && lastRunningModeData.value.segments.isEmpty() && lastTreatmentData.value.therapyEvents.isEmpty()) {
            // No data at all — just populate normalizer so chart renders
            modelProducer.runTransaction {
                lineModel { series(x = normalizerX(maxX), y = NORMALIZER_Y) }
            }
            modeSegmentCountState.intValue = 0
            exerciseDurationCountState.intValue = 0
            generalDurationCountState.intValue = 0
            return@LaunchedEffect
        }

        val activeRunningModeData = lastRunningModeData.value
        val segments = activeRunningModeData.segments
        val activeTreatmentData = lastTreatmentData.value
        // Meter values and BG checks live in the BG graph now, at their own glucose value
        val therapyEvents = activeTreatmentData.therapyEvents.filter { it.isOnBelt() }

        // Prepare running mode segment series data (all modes get a color)
        val rawModeSegments = segments.mapNotNull { segment ->
            val startX = timestampToX(segment.startTime, minTimestamp)
            val endX = timestampToX(segment.endTime, minTimestamp)
            if (endX < minX || startX > maxX) return@mapNotNull null
            val clampedStart = startX.coerceIn(minX, maxX)
            val clampedEnd = endX.coerceIn(minX, maxX)
            if (clampedEnd - clampedStart < 0.5) return@mapNotNull null
            Triple(clampedStart, clampedEnd, segment.mode)
        }

        // Worker provides full coverage (including initial mode from DB query at fromTime).
        // No gap-filling needed here.
        val modeSegmentSeries = rawModeSegments

        // Events with one moment, drawn by BeltIcons
        val iconEvents = therapyEvents
            .filter { it.eventType in BELT_ICON_TYPES }
            .map { timestampToX(it.timestamp, minTimestamp) to it.eventType }
            .filter { it.first in minX..maxX }
            .sortedBy { it.first }

        // Duration events — each gets its own 2-point series
        val exerciseDurationSeries = therapyEvents
            .filter { it.eventType == TherapyEventType.EXERCISE && it.duration > 0 }
            .mapNotNull { event ->
                val startX = timestampToX(event.timestamp, minTimestamp)
                val endX = timestampToX(endOf(event.timestamp, event.duration), minTimestamp)
                if (endX < minX || startX > maxX) return@mapNotNull null
                val cStart = startX.coerceIn(minX, maxX)
                val cEnd = endX.coerceIn(minX, maxX)
                if (cEnd - cStart < 0.5) return@mapNotNull null
                cStart to cEnd
            }
        val generalDurationSeries = therapyEvents
            .filter { it.eventType == TherapyEventType.GENERAL_WITH_DURATION && it.duration > 0 }
            .mapNotNull { event ->
                val startX = timestampToX(event.timestamp, minTimestamp)
                val endX = timestampToX(endOf(event.timestamp, event.duration), minTimestamp)
                if (endX < minX || startX > maxX) return@mapNotNull null
                val cStart = startX.coerceIn(minX, maxX)
                val cEnd = endX.coerceIn(minX, maxX)
                if (cEnd - cStart < 0.5) return@mapNotNull null
                cStart to cEnd
            }

        // Stretches where carbs absorption fell back to the minimum rate
        val failoverStretches = cobFailoverStretches(cobData.failOverPoints.map { it.timestamp }, minTimestamp)

        modelProducer.runTransaction {
            lineModel {
                // 1. Running mode segments — colored background rectangles
                for ((start, end, _) in modeSegmentSeries) {
                    series(x = listOf(start, end), y = listOf(1.0, 1.0))
                }

                // 2. Duration events — one series per event (top of graph)
                for ((start, end) in exerciseDurationSeries) {
                    series(x = listOf(start, end), y = listOf(1.0, 1.0))
                }
                for ((start, end) in generalDurationSeries) {
                    series(x = listOf(start, end), y = listOf(1.0, 1.0))
                }

                // 3. Normalizer (always last)
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }
            extras {
                it[COB_FAILOVER_KEY] = failoverStretches
                it[BELT_ICONS_KEY] = iconEvents
            }
        }

        // Update state after transaction
        renderedModeSegmentsState.value = modeSegmentSeries
        modeSegmentCountState.intValue = modeSegmentSeries.size
        exerciseDurationCountState.intValue = exerciseDurationSeries.size
        generalDurationCountState.intValue = generalDurationSeries.size
    }

    // =========================================================================
    // Line styles
    // =========================================================================

    // Use the rendered segments (with gap fills) for building lines — must match series order
    val renderedModeSegments by renderedModeSegmentsState

    // Duration event line styles (with icons at endpoints)
    val exerciseDurationLine = remember(exerciseColor, activityPainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(exerciseColor)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(activityPainter, tint = exerciseColor),
                    size = 14.dp
                )
            )
        )
    }
    val generalDurationLine = remember(careportalColor, notePainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(careportalColor)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(notePainter, tint = careportalColor),
                    size = 14.dp
                )
            )
        )
    }

    // Normalizer line
    val normalizerLine = remember { createNormalizerLine() }

    // Build lines list dynamically — MUST match series order from LaunchedEffect
    val modeSegmentCount by modeSegmentCountState
    val exerciseDurationCount by exerciseDurationCountState
    val generalDurationCount by generalDurationCountState

    val lines = remember(
        modeSegmentCount, renderedModeSegments,
        exerciseDurationCount, generalDurationCount,
        exerciseDurationLine, generalDurationLine, normalizerLine
    ) {
        buildList {
            // 1. Running mode segment lines (area fill) — from rendered segments (includes gap fills)
            for ((_, _, mode) in renderedModeSegments) {
                add(createModeSegmentLine(mode.beltColor(elementColors)))
            }
            // 2. Duration event lines
            repeat(exerciseDurationCount) { add(exerciseDurationLine) }
            repeat(generalDurationCount) { add(generalDurationLine) }
            // 3. Normalizer (always last)
            add(normalizerLine)
        }
    }

    // Time formatter and axis config — same as other graphs for alignment
    val bgReadings by viewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // Press and hold, or a tap, shows the inspect pill. The marker draws nothing, it only hands the
    // time under the finger to the shared inspect state.
    val inspectMarker = remember(inspect) { inspect?.let { SilentInspectMarker } }
    val defaultMarkerController = CartesianMarkerController.rememberShowOnPress()
    val iconRadiusPx = with(LocalDensity.current) { BELT_ICON_TAP_RADIUS.toPx() }
    val beltController = remember(inspect, iconRadiusPx) { inspect?.beltController(iconRadiusPx) }
    val markerController = beltController ?: defaultMarkerController

    // Now line decoration
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val inspectLine = rememberInspectLine(inspect)
    val clockChanges = rememberClockChangeDecoration(minTimestamp, maxTimestamp, bgReadings)
    // The reporter writes into a StateFlow on every draw pass, see VisibleRangeHolder. The delay limits
    // the updates to 20 per second during a scroll or a zoom.
    val visibleRangeHolder = remember { VisibleRangeHolder() }
    val visibleRangeReporter = rememberVisibleRangeReporter(visibleRangeHolder)
    val currentOnVisibleRangeChanged by rememberUpdatedState(onVisibleRangeChanged)
    LaunchedEffect(visibleRangeHolder) {
        visibleRangeHolder.value.collect {
            currentOnVisibleRangeChanged?.invoke(it)
            delay(50)
        }
    }
    val carbsColor = elementColors.carbs
    // In the carbs colour of the second tier, like the basal on BG
    val cobFailoverBar = remember(carbsColor) { CobFailoverBar(carbsColor.copy(alpha = SECOND_TIER_ALPHA)) }
    val beltIcons = remember(eventIconColor, announcementPainter, clinicalNotesPainter, notePainter) {
        BeltIcons(
            icons = mapOf(
                TherapyEventType.ANNOUNCEMENT to PainterComponent(announcementPainter, tint = eventIconColor),
                TherapyEventType.SETTINGS_EXPORT to PainterComponent(clinicalNotesPainter, tint = eventIconColor),
                TherapyEventType.GENERAL to PainterComponent(notePainter, tint = eventIconColor)
            ),
            countText = TextComponent(textStyle = TextStyle(color = eventIconColor, fontSize = BELT_ICON_COUNT_FONT_SIZE))
        )
    }
    val beltDecorations = remember(cobFailoverBar, beltIcons, nowLine, visibleRangeReporter, clockChanges, inspectLine) {
        listOfNotNull(cobFailoverBar, beltIcons, nowLine, visibleRangeReporter, clockChanges, inspectLine)
    }

    val rangeProvider = remember(maxX) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = 1.0)
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                rangeProvider = rangeProvider
            ),
            marker = inspectMarker,
            markerVisibilityListener = inspect?.visibilityListener,
            markerController = markerController,
            decorations = beltDecorations,
            startAxis = VerticalAxis.rememberStart(
                label = rememberTextComponent(
                    style = TextStyle(color = Color.Transparent),
                    minWidth = TextComponent.MinWidth.fixed(30.dp)
                ),
                // No tick, as on the graphs: then all the left axes have the same width and the hour lines line up
                tick = null,
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 2 }) },
                // No frame and no axis lines: the belt is a strip of icons and mode colours
                line = null,
                guideline = null
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = timeFormatter,
                itemPlacer = bottomAxisItemPlacer,
                label = null,
                tick = null,
                line = null,
                // The hour lines, dotted as on the BG graph, so they run on into it
                guideline = rememberGridLine()
            ),
            getXStep = { _, _, _ -> 1.0 }
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        scrollState = scrollState,
        zoomState = zoomState
    )
}

/**
 * Creates an invisible line with a light area fill for a running mode segment: light, so a long
 * stretch in one mode does not outweigh the BG graph below. The inspect pill shows the full colour.
 * The line sits at Y=1.0 with area fill extending to Y=0 (full chart height).
 */
private fun createModeSegmentLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        areaFill = if (color == Color.Transparent) null
        else LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.15f)))
    )

/**
 * Whether the belt draws this event. One with a glucose value (a meter value, a BG check) is drawn
 * in the BG graph at that value instead.
 */
internal fun TherapyEventGraphPoint.isOnBelt(): Boolean =
    eventType != TherapyEventType.MBG && eventType != TherapyEventType.FINGER_STICK

/** The belt's icon for an event, also used in the inspect pill. */
internal fun TherapyEventType.beltIcon(): ImageVector = when (this) {
    TherapyEventType.ANNOUNCEMENT          -> IcAnnouncement
    TherapyEventType.SETTINGS_EXPORT       -> IcClinicalNotes
    TherapyEventType.EXERCISE              -> IcActivity
    TherapyEventType.GENERAL,
    TherapyEventType.GENERAL_WITH_DURATION -> IcNote
    TherapyEventType.MBG,
    TherapyEventType.FINGER_STICK          -> IcBgCheck
}

/** The belt's colour for an event, also used in the inspect pill. */
internal fun TherapyEventType.beltColor(colors: ElementColors): Color = when (this) {
    TherapyEventType.ANNOUNCEMENT          -> colors.announcement
    TherapyEventType.EXERCISE              -> colors.exercise
    TherapyEventType.SETTINGS_EXPORT,
    TherapyEventType.GENERAL,
    TherapyEventType.GENERAL_WITH_DURATION -> colors.careportal
    TherapyEventType.MBG,
    TherapyEventType.FINGER_STICK          -> colors.bgCheck
}

/** The belt's colour for a running mode, also used in the inspect pill. Closed loop is not coloured. */
internal fun RM.Mode.beltColor(colors: ElementColors): Color = when (this) {
    RM.Mode.CLOSED_LOOP       -> Color.Transparent
    RM.Mode.RESUME            -> Color.Transparent
    RM.Mode.OPEN_LOOP         -> colors.loopOpened
    RM.Mode.CLOSED_LOOP_LGS   -> colors.loopLgs
    RM.Mode.DISABLED_LOOP     -> colors.loopDisabled
    RM.Mode.SUPER_BOLUS       -> colors.loopSuperBolus
    RM.Mode.DISCONNECTED_PUMP -> colors.loopDisconnected
    RM.Mode.SUSPENDED_BY_PUMP,
    RM.Mode.SUSPENDED_BY_USER,
    RM.Mode.SUSPENDED_BY_DST  -> colors.loopSuspended
}

/**
 * Custom Vico Component that renders a Compose [Painter] (e.g., VectorPainter from ImageVector).
 * Used for therapy event icon markers in the belt graph.
 *
 * Vico's [DrawingContext] provides [DrawingContext.mutableDrawScope] which is a full [DrawScope],
 * allowing standard Compose drawing operations including [Painter.draw].
 */
internal class PainterComponent(
    private val painter: Painter,
    private val tint: Color? = null
) : Component {

    override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return
        with(context) {
            with(mutableDrawScope) {
                size = Size(width, height)
                translate(left, top) {
                    with(painter) {
                        draw(
                            size = Size(width, height),
                            colorFilter = tint?.let { ColorFilter.tint(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A thin bar in the carbs colour along the bottom of the belt, one segment for each stretch in which
 * carbs absorption fell back to the minimum rate ("guessed" absorption). The belt is always shown, so
 * this is seen without a COB graph. On the belt, not on the bottom edge of BG, where it covered the SMBs.
 *
 * Drawn under the layers, so an event icon stays on top of it. The running mode fills are light
 * enough to let it show through.
 */
private class CobFailoverBar(private val color: Color) : Decoration {

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            if (ranges.xStep == 0.0) return
            val stretches = model.extraStore.getOrNull(COB_FAILOVER_KEY) ?: return
            if (stretches.isEmpty()) return
            fun canvasX(x: Double): Float =
                layerBounds.left + layerDimensions.startPadding +
                    layerDimensions.xSpacing * ((x - ranges.minX) / ranges.xStep).toFloat() - scroll
            // Thin: the bar runs as long as the guess, and 4 dp high it read as a stripe across the belt
            val height = AapsSpacing.extraSmall.pixels
            for (stretch in stretches) {
                val left = canvasX(stretch.start).coerceAtLeast(layerBounds.left)
                val right = canvasX(stretch.endInclusive).coerceAtMost(layerBounds.right)
                if (right <= left) continue
                with(mutableDrawScope) {
                    drawRect(color = color, topLeft = Offset(left, layerBounds.bottom - height), size = Size(right - left, height))
                }
            }
        }
    }
}

/** The event types [BeltIcons] draws: the ones with one moment and no duration. */
private val BELT_ICON_TYPES = setOf(TherapyEventType.ANNOUNCEMENT, TherapyEventType.SETTINGS_EXPORT, TherapyEventType.GENERAL)

/**
 * Which of the [events] (x, sorted by x) are drawn, as (index of the event drawn, how many events it
 * stands for). An event closer than [iconWidthPx] to the one drawn before it would overlap it, so it
 * joins that one. [pxPerX] turns x into pixels at the current zoom, so zooming in pulls them apart.
 */
internal fun beltIconGroups(events: List<Double>, pxPerX: Float, iconWidthPx: Float): List<Pair<Int, Int>> {
    val groups = mutableListOf<Pair<Int, Int>>()
    for ((index, x) in events.withIndex()) {
        val last = groups.lastOrNull()
        if (last != null && (x - events[last.first]) * pxPerX < iconWidthPx) groups[groups.size - 1] = last.first to last.second + 1
        else groups += index to 1
    }
    return groups
}

/**
 * The icons of the events with one moment. Drawn here and not as Vico points, so icons that would
 * overlap at the current zoom become one icon with the number of events next to it. On top of each
 * other they read as a grey blob. Zooming in pulls them apart. A tap shows the event nearest to the
 * finger, see `beltTapTimestamp`.
 */
private class BeltIcons(
    private val icons: Map<TherapyEventType, PainterComponent>,
    private val countText: TextComponent
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            if (ranges.xStep == 0.0) return
            val events = model.extraStore.getOrNull(BELT_ICONS_KEY) ?: return
            if (events.isEmpty()) return
            val pxPerX = (layerDimensions.xSpacing / ranges.xStep).toFloat()
            val size = BELT_ICON_SIZE.pixels
            val half = size / 2
            val centerY = (layerBounds.top + layerBounds.bottom) / 2
            for ((index, count) in beltIconGroups(events.map { it.first }, pxPerX, size)) {
                val (x, type) = events[index]
                val px = layerBounds.left + layerDimensions.startPadding + pxPerX * (x - ranges.minX).toFloat() - scroll
                // Only an icon fully inside the belt, so neither it nor its number reaches over the axis column
                if (px - half < layerBounds.left || px + half > layerBounds.right) continue
                icons[type]?.draw(context, px - half, centerY - half, px + half, centerY + half)
                if (count > 1) countText.draw(context, count.toString(), px + half, centerY - half, horizontalPosition = Position.Horizontal.End)
            }
        }
    }
}
