package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcNote
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.DrawingContext
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/** ExtraStore key for therapy event lookup by X position. */
private val therapyEventMapKey = ExtraStore.Key<Map<Double, TherapyEventGraphPoint>>()

/** ExtraStore key for running mode segments (start, end, mode). */
private val modeSegmentsKey = ExtraStore.Key<List<Triple<Double, Double, RM.Mode>>>()

/**
 * Treatment Belt Graph — thin graph above BG graph showing:
 * - Running mode background colors (colored rectangles per mode segment)
 * - Therapy event icons (MBG, Finger Stick, Announcement, Exercise, etc.)
 *
 * Architecture:
 * - Fixed Y range (0.0 to 1.0) — all events plotted at Y=0.5
 * - Running mode segments: 2-point area-fill series per segment (Y=1.0, full height)
 * - Therapy events: point markers grouped by type
 * - Duration events: 2-point line series per event
 * - No axis labels (clean belt appearance)
 * - Same 3 pillars as other graphs: fixed X range, getXStep = { 1.0 }, normalizer line
 *
 * Dynamic series order (must match lines list):
 *   1. Running mode segments (0..N, each = 2-point at Y=1.0 with area fill)
 *   2. MBG points (conditional)
 *   3. Finger Stick points (conditional)
 *   4. Announcement points (conditional)
 *   5. Settings Export points (conditional)
 *   6. General event points (conditional)
 *   7. Exercise duration lines (0..M, each = 2-point at Y=1.0, top)
 *   8. General with duration lines (0..K, each = 2-point at Y=1.0, top)
 *   9. Normalizer (always last)
 */
@Composable
fun TreatmentBeltGraphCompose(
    viewModel: GraphViewModel,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    modifier: Modifier = Modifier
) {
    // Collect flows independently
    val runningModeData by viewModel.runningModeGraphFlow.collectAsStateWithLifecycle()
    val treatmentGraphData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000L
        dayAgo to now
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    // Colors from theme
    val elementColors = AapsTheme.elementColors
    val bgCheckColor = elementColors.bgCheck
    val announcementColor = elementColors.announcement
    val careportalColor = elementColors.careportal
    val exerciseColor = elementColors.exercise

    // Icon painters for therapy events
    val bgCheckPainter = rememberVectorPainter(IcBgCheck)
    val announcementPainter = rememberVectorPainter(IcAnnouncement)
    val activityPainter = rememberVectorPainter(IcActivity)
    val notePainter = rememberVectorPainter(IcNote)
    val clinicalNotesPainter = rememberVectorPainter(IcClinicalNotes)

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
    val hasMbgState = remember { mutableStateOf(false) }
    val hasFingerStickState = remember { mutableStateOf(false) }
    val hasAnnouncementState = remember { mutableStateOf(false) }
    val hasSettingsExportState = remember { mutableStateOf(false) }
    val hasGeneralState = remember { mutableStateOf(false) }
    val exerciseDurationCountState = remember { mutableIntStateOf(0) }
    val generalDurationCountState = remember { mutableIntStateOf(0) }

    // Cache last non-empty running mode data to survive reset() cycles
    val lastRunningModeData = remember { mutableStateOf(runningModeData) }
    if (runningModeData.segments.isNotEmpty()) {
        lastRunningModeData.value = runningModeData
    }

    LaunchedEffect(runningModeData, treatmentGraphData, stableTimeRange) {
        // Don't skip when hasRealTimeRange is false — always populate model to avoid flicker.
        // Use fallback time range so the chart skeleton (axes/border) always renders.
        if (!hasRealTimeRange && lastRunningModeData.value.segments.isEmpty() && lastTreatmentData.value.therapyEvents.isEmpty()) {
            // No data at all — just populate normalizer so chart renders
            modelProducer.runTransaction {
                lineSeries { series(x = normalizerX(maxX), y = NORMALIZER_Y) }
            }
            modeSegmentCountState.intValue = 0
            hasMbgState.value = false
            hasFingerStickState.value = false
            hasAnnouncementState.value = false
            hasSettingsExportState.value = false
            hasGeneralState.value = false
            exerciseDurationCountState.intValue = 0
            generalDurationCountState.intValue = 0
            return@LaunchedEffect
        }

        val activeRunningModeData = lastRunningModeData.value
        val segments = activeRunningModeData.segments
        val activeTreatmentData = lastTreatmentData.value
        val therapyEvents = activeTreatmentData.therapyEvents

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

        // Group therapy events by type (non-duration events)
        val mbgPoints = therapyEvents
            .filter { it.eventType == TherapyEventType.MBG }
            .map { timestampToX(it.timestamp, minTimestamp) to 0.5 }
        val fingerStickPoints = therapyEvents
            .filter { it.eventType == TherapyEventType.FINGER_STICK }
            .map { timestampToX(it.timestamp, minTimestamp) to 0.5 }
        val announcementPoints = therapyEvents
            .filter { it.eventType == TherapyEventType.ANNOUNCEMENT }
            .map { timestampToX(it.timestamp, minTimestamp) to 0.5 }
        val settingsExportPoints = therapyEvents
            .filter { it.eventType == TherapyEventType.SETTINGS_EXPORT }
            .map { timestampToX(it.timestamp, minTimestamp) to 0.5 }
        val generalPoints = therapyEvents
            .filter { it.eventType == TherapyEventType.GENERAL }
            .map { timestampToX(it.timestamp, minTimestamp) to 0.5 }

        // Duration events — each gets its own 2-point series
        val exerciseDurationSeries = therapyEvents
            .filter { it.eventType == TherapyEventType.EXERCISE && it.duration > 0 }
            .mapNotNull { event ->
                val startX = timestampToX(event.timestamp, minTimestamp)
                val endX = timestampToX(event.timestamp + event.duration, minTimestamp)
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
                val endX = timestampToX(event.timestamp + event.duration, minTimestamp)
                if (endX < minX || startX > maxX) return@mapNotNull null
                val cStart = startX.coerceIn(minX, maxX)
                val cEnd = endX.coerceIn(minX, maxX)
                if (cEnd - cStart < 0.5) return@mapNotNull null
                cStart to cEnd
            }

        var hasMbg = false
        var hasFingerStick = false
        var hasAnnouncement = false
        var hasSettingsExport = false
        var hasGeneral = false

        modelProducer.runTransaction {
            lineSeries {
                // 1. Running mode segments — colored background rectangles
                for ((start, end, _) in modeSegmentSeries) {
                    series(x = listOf(start, end), y = listOf(1.0, 1.0))
                }

                // 2. Non-duration therapy events (grouped by type)
                val mbgFiltered = filterToRange(mbgPoints, minX, maxX)
                if (mbgFiltered.isNotEmpty()) {
                    series(x = mbgFiltered.map { it.first }, y = mbgFiltered.map { it.second })
                    hasMbg = true
                }
                val fsFiltered = filterToRange(fingerStickPoints, minX, maxX)
                if (fsFiltered.isNotEmpty()) {
                    series(x = fsFiltered.map { it.first }, y = fsFiltered.map { it.second })
                    hasFingerStick = true
                }
                val annFiltered = filterToRange(announcementPoints, minX, maxX)
                if (annFiltered.isNotEmpty()) {
                    series(x = annFiltered.map { it.first }, y = annFiltered.map { it.second })
                    hasAnnouncement = true
                }
                val seFiltered = filterToRange(settingsExportPoints, minX, maxX)
                if (seFiltered.isNotEmpty()) {
                    series(x = seFiltered.map { it.first }, y = seFiltered.map { it.second })
                    hasSettingsExport = true
                }
                val genFiltered = filterToRange(generalPoints, minX, maxX)
                if (genFiltered.isNotEmpty()) {
                    series(x = genFiltered.map { it.first }, y = genFiltered.map { it.second })
                    hasGeneral = true
                }

                // 3. Duration events — one series per event (top of graph)
                for ((start, end) in exerciseDurationSeries) {
                    series(x = listOf(start, end), y = listOf(1.0, 1.0))
                }
                for ((start, end) in generalDurationSeries) {
                    series(x = listOf(start, end), y = listOf(1.0, 1.0))
                }

                // 4. Normalizer (always last)
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }
            extras { store ->
                store[therapyEventMapKey] = buildMap {
                    for (event in therapyEvents) {
                        val x = timestampToX(event.timestamp, minTimestamp)
                        put(x, event)
                        if (event.duration > 0) {
                            put(timestampToX(event.timestamp + event.duration, minTimestamp), event)
                        }
                    }
                }
                store[modeSegmentsKey] = modeSegmentSeries
            }
        }

        // Update state after transaction
        renderedModeSegmentsState.value = modeSegmentSeries
        modeSegmentCountState.intValue = modeSegmentSeries.size
        hasMbgState.value = hasMbg
        hasFingerStickState.value = hasFingerStick
        hasAnnouncementState.value = hasAnnouncement
        hasSettingsExportState.value = hasSettingsExport
        hasGeneralState.value = hasGeneral
        exerciseDurationCountState.intValue = exerciseDurationSeries.size
        generalDurationCountState.intValue = generalDurationSeries.size
    }

    // =========================================================================
    // Line styles
    // =========================================================================

    // Build mode color mapping function
    fun modeColor(mode: RM.Mode): Color = when (mode) {
        RM.Mode.CLOSED_LOOP       -> Color.Transparent
        RM.Mode.RESUME            -> Color.Transparent
        RM.Mode.OPEN_LOOP         -> elementColors.loopOpened
        RM.Mode.CLOSED_LOOP_LGS   -> elementColors.loopLgs
        RM.Mode.DISABLED_LOOP     -> elementColors.loopDisabled
        RM.Mode.SUPER_BOLUS       -> elementColors.loopSuperBolus
        RM.Mode.DISCONNECTED_PUMP -> elementColors.loopDisconnected
        RM.Mode.SUSPENDED_BY_PUMP,
        RM.Mode.SUSPENDED_BY_USER,
        RM.Mode.SUSPENDED_BY_DST  -> elementColors.loopSuspended
    }

    // Use the rendered segments (with gap fills) for building lines — must match series order
    val renderedModeSegments by renderedModeSegmentsState

    // Therapy event icon point styles
    val mbgLine = remember(bgCheckColor, bgCheckPainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(bgCheckPainter, tint = bgCheckColor.copy(alpha = 0.5f)),
                    size = 16.dp
                )
            )
        )
    }
    val fingerStickLine = remember(bgCheckColor, bgCheckPainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(bgCheckPainter, tint = bgCheckColor),
                    size = 16.dp
                )
            )
        )
    }
    val announcementLine = remember(announcementColor, announcementPainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(announcementPainter, tint = announcementColor),
                    size = 16.dp
                )
            )
        )
    }
    val settingsExportLine = remember(careportalColor, clinicalNotesPainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(clinicalNotesPainter, tint = careportalColor),
                    size = 16.dp
                )
            )
        )
    }
    val generalLine = remember(careportalColor, notePainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(notePainter, tint = careportalColor),
                    size = 16.dp
                )
            )
        )
    }

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
    val hasMbg by hasMbgState
    val hasFingerStick by hasFingerStickState
    val hasAnnouncement by hasAnnouncementState
    val hasSettingsExport by hasSettingsExportState
    val hasGeneral by hasGeneralState
    val exerciseDurationCount by exerciseDurationCountState
    val generalDurationCount by generalDurationCountState

    val lines = remember(
        modeSegmentCount, renderedModeSegments,
        hasMbg, hasFingerStick, hasAnnouncement, hasSettingsExport, hasGeneral,
        exerciseDurationCount, generalDurationCount,
        mbgLine, fingerStickLine, announcementLine, settingsExportLine, generalLine,
        exerciseDurationLine, generalDurationLine, normalizerLine
    ) {
        buildList {
            // 1. Running mode segment lines (area fill) — from rendered segments (includes gap fills)
            for ((_, _, mode) in renderedModeSegments) {
                add(createModeSegmentLine(modeColor(mode)))
            }
            // 2. Non-duration therapy event lines
            if (hasMbg) add(mbgLine)
            if (hasFingerStick) add(fingerStickLine)
            if (hasAnnouncement) add(announcementLine)
            if (hasSettingsExport) add(settingsExportLine)
            if (hasGeneral) add(generalLine)
            // 3. Duration event lines
            repeat(exerciseDurationCount) { add(exerciseDurationLine) }
            repeat(generalDurationCount) { add(generalDurationLine) }
            // 4. Normalizer (always last)
            add(normalizerLine)
        }
    }

    // Time formatter and axis config — same as other graphs for alignment
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // =========================================================================
    // Tap marker (tooltip on tap)
    // =========================================================================
    val modeNameMap = rememberModeNameMap()
    val beltMarker = rememberBeltMarker(modeNameMap)

    // Now line decoration
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val beltDecorations = remember(nowLine) { listOf(nowLine) }

    val rangeProvider = remember(maxX) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = 1.0)
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                rangeProvider = rangeProvider
            ),
            marker = beltMarker,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
            decorations = beltDecorations,
            startAxis = VerticalAxis.rememberStart(
                label = rememberTextComponent(
                    style = TextStyle(color = Color.Transparent),
                    minWidth = TextComponent.MinWidth.fixed(30.dp)
                ),
                tick = rememberAxisTickComponent(fill = Fill(Color.Transparent)),
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 2 }) }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = timeFormatter,
                itemPlacer = bottomAxisItemPlacer,
                label = null,
                tick = null
            ),
            getXStep = { 1.0 }
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
 * Creates an invisible line with semi-transparent area fill for a running mode segment.
 * The line sits at Y=1.0 with area fill extending to Y=0 (full chart height).
 */
private fun createModeSegmentLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        areaFill = if (color == Color.Transparent) null
        else LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.3f)))
    )

/**
 * Resolves localized display names for each [RM.Mode].
 */
@Composable
private fun rememberModeNameMap(): Map<RM.Mode, String> {
    val closedLoop = stringResource(app.aaps.core.ui.R.string.closedloop)
    val openLoop = stringResource(app.aaps.core.ui.R.string.openloop)
    val lgs = stringResource(app.aaps.core.ui.R.string.lowglucosesuspend)
    val disabledLoop = stringResource(app.aaps.core.ui.R.string.disabled_loop)
    val superbolus = stringResource(app.aaps.core.ui.R.string.superbolus)
    val disconnected = stringResource(app.aaps.core.ui.R.string.disconnected)
    val pumpSuspended = stringResource(app.aaps.core.ui.R.string.pumpsuspended)
    val loopSuspended = stringResource(app.aaps.core.ui.R.string.loopsuspended)
    val dstSuspended = stringResource(app.aaps.core.ui.R.string.loop_suspended_by_dst)
    return mapOf(
        RM.Mode.CLOSED_LOOP to closedLoop,
        RM.Mode.RESUME to closedLoop,
        RM.Mode.OPEN_LOOP to openLoop,
        RM.Mode.CLOSED_LOOP_LGS to lgs,
        RM.Mode.DISABLED_LOOP to disabledLoop,
        RM.Mode.SUPER_BOLUS to superbolus,
        RM.Mode.DISCONNECTED_PUMP to disconnected,
        RM.Mode.SUSPENDED_BY_PUMP to pumpSuspended,
        RM.Mode.SUSPENDED_BY_USER to loopSuspended,
        RM.Mode.SUSPENDED_BY_DST to dstSuspended,
    )
}

/**
 * Creates a marker for the treatment belt graph that shows therapy event details
 * or running mode name on tap.
 */
@Composable
private fun rememberBeltMarker(modeNameMap: Map<RM.Mode, String>): DefaultCartesianMarker {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    val labelBackground = remember(surfaceColor, outlineColor) {
        ShapeComponent(
            fill = Fill(surfaceColor),
            shape = RoundedCornerShape(4.dp),
            strokeFill = Fill(outlineColor),
            strokeThickness = 1.dp
        )
    }

    val markerLabel = rememberTextComponent(
        style = TextStyle(color = onSurfaceColor, fontSize = 15.sp),
        padding = Insets(horizontal = 10.dp, vertical = 5.dp),
        background = labelBackground
    )

    val valueFormatter = remember(modeNameMap) {
        DefaultCartesianMarker.ValueFormatter { context, targets ->
            val x = targets.firstOrNull()?.x ?: return@ValueFormatter ""
            val extraStore = context.model.extraStore

            // Check for therapy event at tapped X position
            val eventMap = extraStore.getOrNull(therapyEventMapKey)
            val event = eventMap?.get(x)
            if (event != null) return@ValueFormatter event.label

            // Fall back to running mode name for the segment containing X
            val segments = extraStore.getOrNull(modeSegmentsKey)
            if (segments != null) {
                val segment = segments.find { (start, end, _) -> x in start..end }
                if (segment != null) {
                    return@ValueFormatter modeNameMap[segment.third] ?: segment.third.name
                }
            }

            ""
        }
    }

    return rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = valueFormatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        indicator = null,
        guideline = null
    )
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
