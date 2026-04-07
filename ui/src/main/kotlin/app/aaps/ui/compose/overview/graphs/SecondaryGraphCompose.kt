package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.graph.vico.AdaptiveStep
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.DeviationType
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.ui.compose.AapsTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * General-purpose secondary graph composable.
 *
 * Renders any combination of series types on a single chart.
 * Each series type has its own color and rendering style:
 * - IOB: Blue line with area fill + bolus markers (SMBs, normal boluses, extended boluses)
 * - COB: Orange adaptive-step line with area fill + carbs markers + failover dots
 * - Simple line series (AbsIOB, BGI, Sensitivity, VarSens, DevSlope, HR, Steps): Colored line with gradient fill
 * - Deviations: Colored line (TODO: bar rendering in future)
 */
@Composable
fun SecondaryGraphCompose(
    viewModel: GraphViewModel,
    seriesTypes: List<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    activityOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (seriesTypes.isEmpty()) return

    // Ensure DEVIATIONS/DEV_SLOPE is always primary — DEVIATIONS requires the column layer,
    // DEV_SLOPE needs primary to render both dsMax and dsMin lines
    val orderedTypes = remember(seriesTypes) {
        val mustBePrimary = setOf(SeriesType.DEVIATIONS, SeriesType.DEV_SLOPE)
        if (seriesTypes.size >= 2 && seriesTypes[0] !in mustBePrimary && seriesTypes[1] in mustBePrimary)
            listOf(seriesTypes[1], seriesTypes[0])
        else
            seriesTypes
    }

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000L
        dayAgo to now
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val minX = 0.0
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }
    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // =========================================================================
    // Collect data flows — only for selected series types
    // =========================================================================

    // Primary series = left axis (orderedTypes[0]), secondary series = right axis (orderedTypes[1])
    val primaryType = orderedTypes[0]
    val secondaryType = orderedTypes.getOrNull(1)
    val isDualAxis = secondaryType != null

    val hasIob = primaryType == SeriesType.IOB
    val hasCob = primaryType == SeriesType.COB

    // Collect flows for primary series
    val iobData = if (hasIob) viewModel.iobGraphFlow.collectAsStateWithLifecycle().value else null
    val cobData = if (hasCob) viewModel.cobGraphFlow.collectAsStateWithLifecycle().value else null
    val treatmentData = if (hasIob || hasCob) viewModel.treatmentGraphFlow.collectAsStateWithLifecycle().value else null
    val absIobData = if (primaryType == SeriesType.ABS_IOB) viewModel.absIobGraphFlow.collectAsStateWithLifecycle().value else null
    val bgiData = if (primaryType == SeriesType.BGI) viewModel.bgiGraphFlow.collectAsStateWithLifecycle().value else null
    val deviationsData = if (primaryType == SeriesType.DEVIATIONS) viewModel.deviationsGraphFlow.collectAsStateWithLifecycle().value else null
    val ratioData = if (primaryType == SeriesType.SENSITIVITY) viewModel.ratioGraphFlow.collectAsStateWithLifecycle().value else null
    val varSensData = if (primaryType == SeriesType.VAR_SENSITIVITY) viewModel.varSensGraphFlow.collectAsStateWithLifecycle().value else null
    val devSlopeData = if (primaryType == SeriesType.DEV_SLOPE) viewModel.devSlopeGraphFlow.collectAsStateWithLifecycle().value else null
    val hrData = if (primaryType == SeriesType.HEART_RATE) viewModel.heartRateGraphFlow.collectAsStateWithLifecycle().value else null
    val stepsData = if (primaryType == SeriesType.STEPS) viewModel.stepsGraphFlow.collectAsStateWithLifecycle().value else null
    // Activity data: either as primary series OR as overlay (on IOB graph)
    val needsActivity = primaryType == SeriesType.ACTIVITY || (activityOverlay && hasIob)
    val activityData = if (needsActivity) viewModel.activityGraphFlow.collectAsStateWithLifecycle().value else null
    // Basal overlay only when IOB is primary and single-axis (no room for basal with dual-axis)
    val basalData = if (hasIob && !isDualAxis) viewModel.basalGraphFlow.collectAsStateWithLifecycle().value else null

    // Collect flow for secondary (right axis) series
    val secondaryLineData = when (secondaryType) {
        SeriesType.IOB             -> viewModel.iobGraphFlow.collectAsStateWithLifecycle().value.let {
            it.iob.map { p -> GraphDataPoint(p.timestamp, p.value) }
        }

        SeriesType.ABS_IOB         -> viewModel.absIobGraphFlow.collectAsStateWithLifecycle().value.absIob
        SeriesType.COB             -> viewModel.cobGraphFlow.collectAsStateWithLifecycle().value.cob
        SeriesType.BGI             -> viewModel.bgiGraphFlow.collectAsStateWithLifecycle().value.bgi
        SeriesType.DEVIATIONS      -> viewModel.deviationsGraphFlow.collectAsStateWithLifecycle().value.deviations.map {
            GraphDataPoint(it.timestamp, it.value)
        }

        SeriesType.SENSITIVITY     -> viewModel.ratioGraphFlow.collectAsStateWithLifecycle().value.ratio
        SeriesType.VAR_SENSITIVITY -> viewModel.varSensGraphFlow.collectAsStateWithLifecycle().value.varSens
        SeriesType.DEV_SLOPE       -> viewModel.devSlopeGraphFlow.collectAsStateWithLifecycle().value.dsMax
        SeriesType.HEART_RATE      -> viewModel.heartRateGraphFlow.collectAsStateWithLifecycle().value.heartRates
        SeriesType.STEPS           -> viewModel.stepsGraphFlow.collectAsStateWithLifecycle().value.steps
        SeriesType.ACTIVITY        -> viewModel.activityGraphFlow.collectAsStateWithLifecycle().value.activity
        null                       -> emptyList()
    }

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentData ?: TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList())) }
    if (treatmentData != null && (treatmentData.boluses.isNotEmpty() || treatmentData.carbs.isNotEmpty() || treatmentData.extendedBoluses.isNotEmpty())) {
        lastTreatmentData.value = treatmentData
    }

    // =========================================================================
    // Process all series into x/y coordinates
    // =========================================================================

    // Simple line series processing (excludes BASAL — it's a fixed flipped overlay on IOB)
    val processedSimpleSeries = remember(
        stableTimeRange, absIobData, bgiData, ratioData, varSensData, devSlopeData, hrData, stepsData, activityData
    ) {
        if (!hasRealTimeRange) return@remember emptyList()
        buildList {
            absIobData?.absIob?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.ABS_IOB to processPoints(it, minTimestamp, minX, maxX))
            }
            bgiData?.let {
                if (it.bgi.isNotEmpty()) add(SeriesType.BGI to processPoints(it.bgi, minTimestamp, minX, maxX))
                // TODO: differentiate prediction line style (e.g., dashed) from historical
                if (it.bgiPrediction.isNotEmpty()) add(SeriesType.BGI to processPoints(it.bgiPrediction, minTimestamp, minX, maxX))
            }
            // Deviations rendered as column layer only (not as line series)
            ratioData?.ratio?.takeIf { it.isNotEmpty() }?.let { pts ->
                // Stored as 100*(ratio-1), display shifted by +100 to show as percentage (90%, 110%)
                val shifted = pts.map { GraphDataPoint(it.timestamp, it.value + 100.0) }
                add(SeriesType.SENSITIVITY to processPoints(shifted, minTimestamp, minX, maxX))
            }
            varSensData?.varSens?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.VAR_SENSITIVITY to processPoints(it, minTimestamp, minX, maxX))
            }
            devSlopeData?.let {
                if (it.dsMax.isNotEmpty()) add(SeriesType.DEV_SLOPE to processPoints(it.dsMax, minTimestamp, minX, maxX))
                // dsMin tagged separately via DEV_SLOPE_MIN marker (see below)
            }
            hrData?.heartRates?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.HEART_RATE to processPoints(it, minTimestamp, minX, maxX))
            }
            stepsData?.steps?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.STEPS to processPoints(it, minTimestamp, minX, maxX))
            }
            if (primaryType == SeriesType.ACTIVITY) {
                activityData?.let {
                    if (it.activity.isNotEmpty()) add(SeriesType.ACTIVITY to processPoints(it.activity, minTimestamp, minX, maxX))
                    if (it.activityPrediction.isNotEmpty()) add(SeriesType.ACTIVITY to processPoints(it.activityPrediction, minTimestamp, minX, maxX))
                }
            }
        }
    }

    // DevSlope min line — separate from simple series for distinct color
    val processedDevSlopeMin = remember(devSlopeData, stableTimeRange) {
        if (!hasRealTimeRange || devSlopeData == null || devSlopeData.dsMin.isEmpty()) return@remember emptyList()
        processPoints(devSlopeData.dsMin, minTimestamp, minX, maxX)
    }

    // Deviation column data — processed separately for the column layer
    val deviationColumns = remember(deviationsData, stableTimeRange) {
        if (!hasRealTimeRange || deviationsData == null || deviationsData.deviations.isEmpty())
            return@remember null
        val filtered = deviationsData.deviations
            .map { Triple(timestampToX(it.timestamp, minTimestamp), it.value, it.deviationType) }
            .filter { it.first in minX..maxX }
            .sortedBy { it.first }
        if (filtered.isEmpty()) return@remember null
        val allX = filtered.map { it.first }
        val allY = filtered.map { it.second }
        val typeLookup = filtered.associate { it.first to it.third }
        Triple(allX, allY, typeLookup)
    }

    // IOB line processing
    val processedIob = remember(iobData, stableTimeRange) {
        if (!hasRealTimeRange || iobData == null) return@remember emptyList()
        processPoints(iobData.iob, minTimestamp, minX, maxX)
    }

    // IOB treatment overlays processing
    val processedIobTreatments = remember(lastTreatmentData.value, stableTimeRange) {
        if (!hasRealTimeRange || !hasIob) return@remember ProcessedIobOverlays.EMPTY
        processIobOverlays(lastTreatmentData.value, minTimestamp, minX, maxX)
    }

    // COB line processing
    val processedCob = remember(cobData, stableTimeRange) {
        if (!hasRealTimeRange || cobData == null) return@remember Pair(emptyList(), emptyList())
        val cobFiltered = processPoints(cobData.cob, minTimestamp, minX, maxX)
        val failoverFiltered = cobData.failOverPoints.map { timestampToX(it.timestamp, minTimestamp) to it.cobValue }
            .let { filterToRange(it, minX, maxX) }
        cobFiltered to failoverFiltered
    }

    // COB carbs overlay processing
    val processedCarbs = remember(lastTreatmentData.value, stableTimeRange) {
        if (!hasRealTimeRange || !hasCob) return@remember emptyList()
        val pts = lastTreatmentData.value.carbs.map { timestampToX(it.timestamp, minTimestamp) to it.amount }
        filterToRange(pts, minX, maxX)
    }

    // Flipped basal overlay — fixed on IOB graphs, rendered as a second chart layer.
    // Uses negative Y values so area fill goes upward to y=0 (the top of the basal layer).
    val processedBasalProfile = remember(basalData, stableTimeRange) {
        if (!hasRealTimeRange || basalData == null) return@remember emptyList()
        val pts = processPoints(basalData.profileBasal, minTimestamp, minX, maxX)
        pts.map { (x, y) -> x to -y } // negate: y=0 at top, -maxBasal at bottom
    }
    val processedBasalActual = remember(basalData, stableTimeRange) {
        if (!hasRealTimeRange || basalData == null) return@remember emptyList()
        val pts = processPoints(basalData.actualBasal, minTimestamp, minX, maxX)
        pts.map { (x, y) -> x to -y }
    }
    val basalMaxY = remember(basalData) {
        if (basalData != null && basalData.maxBasal > 0.0) basalData.maxBasal * 4.0 else 1.0
    }

    // =========================================================================
    // Track which series slots are active (for line style matching)
    // =========================================================================

    // Use a sealed approach: track series slot identifiers
    val activeSlotState = remember { mutableStateOf(emptyList<SeriesSlot>()) }
    val deviationTypeLookup = remember { DeviationTypeLookup() }

    val hasBasalLayer = hasIob && basalData != null && !isDualAxis

    // Activity overlay (scaled to fit within IOB's Y range, like BG graph)
    val processedActivityOverlay = remember(activityOverlay, hasIob, activityData, processedIob, stableTimeRange) {
        if (!hasRealTimeRange || !activityOverlay || !hasIob || activityData == null) return@remember Pair(emptyList(), emptyList())
        val maxAct = activityData.maxActivity
        if (maxAct <= 0.0) return@remember Pair(emptyList(), emptyList())
        val iobMaxY = processedIob.maxOfOrNull { it.second }?.coerceAtLeast(0.1) ?: 0.1
        val scale = iobMaxY * 0.8 / maxAct
        val hist = activityData.activity
            .map { timestampToX(it.timestamp, minTimestamp) to (it.value * scale) }
            .let { filterToRange(it, minX, maxX) }
        val pred = activityData.activityPrediction
            .map { timestampToX(it.timestamp, minTimestamp) to (it.value * scale) }
            .let { filterToRange(it, minX, maxX) }
        hist to pred
    }

    // Process secondary (right axis) series
    val processedSecondary = remember(secondaryLineData, stableTimeRange) {
        if (!hasRealTimeRange || secondaryLineData.isEmpty()) return@remember emptyList()
        processPoints(secondaryLineData, minTimestamp, minX, maxX)
    }

    LaunchedEffect(processedSimpleSeries, processedDevSlopeMin, deviationColumns, processedIob, processedIobTreatments, processedCob, processedCarbs, processedBasalProfile, processedBasalActual, processedSecondary, processedActivityOverlay, maxX) {
        if (!hasRealTimeRange) return@LaunchedEffect

        val slots = mutableListOf<SeriesSlot>()
        modelProducer.runTransaction {
            // Column layer data (deviations) — must come first to match layer order
            if (deviationColumns != null) {
                val (allX, allY, typeLookup) = deviationColumns
                deviationTypeLookup.map = typeLookup
                columnSeries { series(x = allX, y = allY) }
            } else {
                deviationTypeLookup.map = emptyMap()
                columnSeries { series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0)) }
            }

            // Primary line layer data
            lineSeries {
                // IOB line
                if (processedIob.isNotEmpty()) {
                    series(x = processedIob.map { it.first }, y = processedIob.map { it.second })
                    slots.add(SeriesSlot.IobLine)
                }

                // IOB overlays: SMBs (small, medium, large), normal boluses, extended boluses
                if (processedIobTreatments.smallSmbs.isNotEmpty()) {
                    series(x = processedIobTreatments.smallSmbs.map { it.first }, y = processedIobTreatments.smallSmbs.map { it.second })
                    slots.add(SeriesSlot.SmallSmb)
                }
                if (processedIobTreatments.mediumSmbs.isNotEmpty()) {
                    series(x = processedIobTreatments.mediumSmbs.map { it.first }, y = processedIobTreatments.mediumSmbs.map { it.second })
                    slots.add(SeriesSlot.MediumSmb)
                }
                if (processedIobTreatments.largeSmbs.isNotEmpty()) {
                    series(x = processedIobTreatments.largeSmbs.map { it.first }, y = processedIobTreatments.largeSmbs.map { it.second })
                    slots.add(SeriesSlot.LargeSmb)
                }
                if (processedIobTreatments.normalBoluses.isNotEmpty()) {
                    series(x = processedIobTreatments.normalBoluses.map { it.first }, y = processedIobTreatments.normalBoluses.map { it.second })
                    slots.add(SeriesSlot.NormalBolus)
                }
                for ((start, end, amount) in processedIobTreatments.extBoluses) {
                    series(x = listOf(start, end), y = listOf(amount, amount))
                    slots.add(SeriesSlot.ExtBolus)
                }

                // COB line
                val (cobFiltered, failoverFiltered) = processedCob
                if (cobFiltered.isNotEmpty()) {
                    series(x = cobFiltered.map { it.first }, y = cobFiltered.map { it.second })
                    slots.add(SeriesSlot.CobLine)
                }
                if (failoverFiltered.isNotEmpty()) {
                    series(x = failoverFiltered.map { it.first }, y = failoverFiltered.map { it.second })
                    slots.add(SeriesSlot.FailoverDots)
                }
                if (processedCarbs.isNotEmpty()) {
                    series(x = processedCarbs.map { it.first }, y = processedCarbs.map { it.second })
                    slots.add(SeriesSlot.CarbsMarker)
                }

                // Simple line series
                for ((type, pts) in processedSimpleSeries) {
                    if (pts.isNotEmpty()) {
                        series(x = pts.map { it.first }, y = pts.map { it.second })
                        slots.add(SeriesSlot.SimpleLine(type))
                    }
                }

                // DevSlope min (separate slot for magenta color)
                if (processedDevSlopeMin.isNotEmpty()) {
                    series(x = processedDevSlopeMin.map { it.first }, y = processedDevSlopeMin.map { it.second })
                    slots.add(SeriesSlot.DevSlopeMin)
                }

                // Activity overlay (scaled) — only when activityOverlay=true on IOB graph
                val (actHist, actPred) = processedActivityOverlay
                if (actHist.isNotEmpty()) {
                    series(x = actHist.map { it.first }, y = actHist.map { it.second })
                    slots.add(SeriesSlot.ActivityOverlay)
                }
                if (actPred.isNotEmpty()) {
                    series(x = actPred.map { it.first }, y = actPred.map { it.second })
                    slots.add(SeriesSlot.ActivityOverlay)
                }

                // Normalizer — ensures identical maxPointSize across all charts
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }

            // Block 2 → Basal layer (end axis, flipped) OR secondary series layer (end axis)
            if (hasBasalLayer) {
                lineSeries {
                    if (processedBasalActual.size >= 2) {
                        series(x = processedBasalActual.map { it.first }, y = processedBasalActual.map { it.second })
                    } else {
                        series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    }
                    if (processedBasalProfile.size >= 2) {
                        series(x = processedBasalProfile.map { it.first }, y = processedBasalProfile.map { it.second })
                    } else {
                        series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    }
                }
            } else if (isDualAxis) {
                lineSeries {
                    if (processedSecondary.isNotEmpty()) {
                        series(x = processedSecondary.map { it.first }, y = processedSecondary.map { it.second })
                    } else {
                        series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    }
                    // Normalizer for end axis layer
                    series(x = normalizerX(maxX), y = NORMALIZER_Y)
                }
            }

        }
        activeSlotState.value = slots
    }

    // =========================================================================
    // Line styles
    // =========================================================================

    val seriesColors = rememberSeriesColors()
    val iobLineStyle = rememberIobLineStyles()
    val cobLineStyle = rememberCobLineStyles()
    val normalizerLine = remember { createNormalizerLine() }

    val activeSlots by activeSlotState
    val lines = remember(activeSlots, seriesColors, iobLineStyle, cobLineStyle, normalizerLine) {
        buildList {
            for (slot in activeSlots) {
                add(
                    when (slot) {
                        SeriesSlot.IobLine         -> iobLineStyle.iobLine
                        SeriesSlot.SmallSmb        -> iobLineStyle.smallSmbLine
                        SeriesSlot.MediumSmb       -> iobLineStyle.mediumSmbLine
                        SeriesSlot.LargeSmb        -> iobLineStyle.largeSmbLine
                        SeriesSlot.NormalBolus     -> iobLineStyle.bolusLine
                        SeriesSlot.ExtBolus        -> iobLineStyle.extBolusLine
                        SeriesSlot.CobLine         -> cobLineStyle.cobLine
                        SeriesSlot.FailoverDots    -> cobLineStyle.failoverDotsLine
                        SeriesSlot.CarbsMarker     -> cobLineStyle.carbsLine
                        SeriesSlot.DevSlopeMin     -> createDevSlopeMinLine()
                        SeriesSlot.ActivityOverlay -> createSeriesLine(SeriesType.ACTIVITY, seriesColors)
                        is SeriesSlot.SimpleLine   -> createSeriesLine(slot.type, seriesColors)
                    }
                )
            }
            add(normalizerLine)
        }
    }

    // Basal layer line styles — same as BG graph (profile dashed, actual solid with fill)
    val basalColor = AapsTheme.elementColors.tempBasal
    val basalProfileLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = 1.dp,
                cap = StrokeCap.Round,
                dashLength = 1.dp,
                gapLength = 2.dp
            ),
            areaFill = null,
            pointConnector = Square
        )
    }
    val basalActualLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(basalColor.copy(alpha = 0.3f))),
            pointConnector = Square
        )
    }
    val basalLines = remember(basalActualLine, basalProfileLine) {
        listOf(basalActualLine, basalProfileLine)
    }

    // =========================================================================
    // Chart rendering
    // =========================================================================

    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val decorations = remember(nowLine) { listOf(nowLine) }
    // When basal overlay is active, reserve top 25% for basal by extending primary Y range
    val primaryYMax = remember(hasBasalLayer, processedIob, processedSimpleSeries, processedCob) {
        if (!hasBasalLayer) return@remember null // auto-range when no basal
        val allY = buildList {
            addAll(processedIob.map { it.second })
            for ((_, pts) in processedSimpleSeries) addAll(pts.map { it.second })
            addAll(processedCob.first.map { it.second })
        }
        if (allY.isEmpty()) null
        else {
            val dataMax = allY.max().coerceAtLeast(0.1)
            val dataMin = allY.min().coerceAtMost(0.0)
            dataMin to (dataMax / 0.75) // extend so data fills 75%, top 25% reserved for basal
        }
    }
    val primaryRangeProvider = remember(maxX, primaryYMax) {
        if (primaryYMax != null)
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryYMax.first, maxY = primaryYMax.second)
        else
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)
    }
    // Basal range: 0 at top, -maxBasal*4 at bottom → basal occupies top 25%
    val basalRangeProvider = remember(maxX, basalMaxY) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = -basalMaxY, maxY = 0.0)
    }

    // Secondary axis line style (colored by series type)
    val secondaryAxisColor = secondaryType?.let { seriesColors.colorFor(it) } ?: Color.Gray
    val secondaryAxisLine = remember(secondaryAxisColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(secondaryAxisColor)),
            areaFill = LineCartesianLayer.AreaFill.single(fill = Fill(Color.Transparent)
            )
        )
    }
    val secondaryAxisLines = remember(secondaryAxisLine, normalizerLine) { listOf(secondaryAxisLine, normalizerLine) }
    val secondaryRangeProvider = remember(maxX) { CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX) }

    // Build chart layers
    val primaryLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(lines),
        rangeProvider = primaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.Start
    )

    // Deviation column layer — temperature anomalies pattern, per-entry color.
    // Max thickness ~32dp — larger values inflate Vico's maxPointSize beyond the normalizer,
    // breaking pixel-based scroll/zoom sync with the BG graph.
    val positiveColumn = rememberLineComponent(fill = Fill(DEVIATION_COLOR_POSITIVE), thickness = 32.dp)
    val negativeColumn = rememberLineComponent(fill = Fill(DEVIATION_COLOR_NEGATIVE), thickness = 32.dp)
    val equalColumn = rememberLineComponent(fill = Fill(DEVIATION_COLOR_EQUAL), thickness = 32.dp)
    val uamColumn = rememberLineComponent(fill = Fill(DEVIATION_COLOR_UAM), thickness = 32.dp)
    val csfColumn = rememberLineComponent(fill = Fill(DEVIATION_COLOR_CSF), thickness = 32.dp)
    val deviationColumnProvider = remember(positiveColumn, negativeColumn, equalColumn, uamColumn, csfColumn, deviationTypeLookup) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(
                entry: ColumnCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore
            ) = when (deviationTypeLookup.map[entry.x]) {
                DeviationType.POSITIVE -> positiveColumn
                DeviationType.NEGATIVE -> negativeColumn
                DeviationType.EQUAL    -> equalColumn
                DeviationType.UAM      -> uamColumn
                DeviationType.CSF      -> csfColumn
                null                   -> equalColumn
            }

            override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore) = positiveColumn
        }
    }
    val deviationColumnLayer = rememberColumnCartesianLayer(
        columnProvider = deviationColumnProvider,
        columnCollectionSpacing = 0.dp,
        rangeProvider = primaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.Start
    )

    // Common axis components
    val startAxis = VerticalAxis.rememberStart(
        label = rememberTextComponent(
            style = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            minWidth = TextComponent.MinWidth.fixed(30.dp)
        ),
        guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    )
    val bottomAxis = HorizontalAxis.rememberBottom(
        valueFormatter = timeFormatter,
        itemPlacer = bottomAxisItemPlacer,
        label = rememberTextComponent(
            style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
        ),
        guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    )

    if (hasBasalLayer) {
        val basalLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(basalLines),
            rangeProvider = basalRangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.End
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                deviationColumnLayer, primaryLayer, basalLayer,
                startAxis = startAxis,
                bottomAxis = bottomAxis, decorations = decorations, getXStep = { 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp),
            scrollState = scrollState, zoomState = zoomState
        )
    } else if (isDualAxis) {
        val secondaryLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(secondaryAxisLines),
            rangeProvider = secondaryRangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.End
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                deviationColumnLayer, primaryLayer, secondaryLayer,
                startAxis = startAxis,
                bottomAxis = bottomAxis, decorations = decorations, getXStep = { 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp),
            scrollState = scrollState, zoomState = zoomState
        )
    } else {
        CartesianChartHost(
            chart = rememberCartesianChart(
                deviationColumnLayer, primaryLayer,
                startAxis = startAxis,
                bottomAxis = bottomAxis, decorations = decorations, getXStep = { 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp),
            scrollState = scrollState, zoomState = zoomState
        )
    }
}

// =========================================================================
// Series slot identifiers (for matching line styles to data series)
// =========================================================================

private sealed class SeriesSlot {
    data object IobLine : SeriesSlot()
    data object SmallSmb : SeriesSlot()
    data object MediumSmb : SeriesSlot()
    data object LargeSmb : SeriesSlot()
    data object NormalBolus : SeriesSlot()
    data object ExtBolus : SeriesSlot()
    data object CobLine : SeriesSlot()
    data object FailoverDots : SeriesSlot()
    data object CarbsMarker : SeriesSlot()
    data class SimpleLine(val type: SeriesType) : SeriesSlot()
    data object DevSlopeMin : SeriesSlot()
    data object ActivityOverlay : SeriesSlot()
}

// =========================================================================
// Data processing helpers
// =========================================================================

@Suppress("SameParameterValue")
private fun processPoints(points: List<GraphDataPoint>, minTimestamp: Long, minX: Double, maxX: Double): List<Pair<Double, Double>> {
    val pts = points.map { timestampToX(it.timestamp, minTimestamp) to it.value }
    return filterToRange(pts, minX, maxX)
}

/** Process IOB treatment overlays: split SMBs by size, extract boluses and extended boluses */
@Suppress("SameParameterValue")
private fun processIobOverlays(data: TreatmentGraphData, minTimestamp: Long, minX: Double, maxX: Double): ProcessedIobOverlays {
    // Filter SMBs to visible range before computing size thresholds (prevents off-screen outliers from distorting buckets)
    val smbs = data.boluses.filter { it.bolusType == BolusType.SMB && timestampToX(it.timestamp, minTimestamp) in minX..maxX }

    var smallSmbs: List<Pair<Double, Double>> = emptyList()
    var mediumSmbs: List<Pair<Double, Double>> = emptyList()
    var largeSmbs: List<Pair<Double, Double>> = emptyList()

    if (smbs.isNotEmpty()) {
        if (smbs.size <= 1 || smbs.minOf { it.amount } == smbs.maxOf { it.amount }) {
            mediumSmbs = smbs.map { timestampToX(it.timestamp, minTimestamp) to 0.0 }
        } else {
            val minAmount = smbs.minOf { it.amount }
            val range = smbs.maxOf { it.amount } - minAmount
            val smallThreshold = minAmount + range / 3.0
            val largeThreshold = minAmount + 2.0 * range / 3.0

            val small = mutableListOf<Pair<Double, Double>>()
            val medium = mutableListOf<Pair<Double, Double>>()
            val large = mutableListOf<Pair<Double, Double>>()

            for (smb in smbs) {
                val point = timestampToX(smb.timestamp, minTimestamp) to 0.0
                when {
                    smb.amount < smallThreshold -> small.add(point)
                    smb.amount < largeThreshold -> medium.add(point)
                    else                        -> large.add(point)
                }
            }
            smallSmbs = small
            mediumSmbs = medium
            largeSmbs = large
        }
    }

    val normalBoluses = data.boluses.filter { it.bolusType == BolusType.NORMAL }
    val bolusFiltered = if (normalBoluses.isNotEmpty()) {
        filterToRange(normalBoluses.map { timestampToX(it.timestamp, minTimestamp) to it.amount }, minX, maxX)
    } else emptyList()

    val extBoluses = data.extendedBoluses.mapNotNull { eb ->
        val startX = timestampToX(eb.timestamp, minTimestamp)
        val endX = timestampToX(eb.timestamp + eb.duration, minTimestamp)
        if (endX < minX || startX > maxX) return@mapNotNull null
        val clampedStart = startX.coerceIn(minX, maxX)
        val clampedEnd = endX.coerceIn(minX, maxX)
        if (clampedStart == clampedEnd) return@mapNotNull null
        Triple(clampedStart, clampedEnd, eb.amount)
    }

    return ProcessedIobOverlays(
        filterToRange(smallSmbs, minX, maxX),
        filterToRange(mediumSmbs, minX, maxX),
        filterToRange(largeSmbs, minX, maxX),
        bolusFiltered, extBoluses
    )
}

private data class ProcessedIobOverlays(
    val smallSmbs: List<Pair<Double, Double>>,
    val mediumSmbs: List<Pair<Double, Double>>,
    val largeSmbs: List<Pair<Double, Double>>,
    val normalBoluses: List<Pair<Double, Double>>,
    val extBoluses: List<Triple<Double, Double, Double>>
) {

    companion object {

        val EMPTY = ProcessedIobOverlays(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

// =========================================================================
// Line styles
// =========================================================================

/** Color map for simple line series */
data class SeriesColors(
    val iob: Color,
    val absIob: Color,
    val cob: Color,
    val bgi: Color,
    val deviations: Color,
    val sensitivity: Color,
    val varSensitivity: Color,
    val devSlope: Color,
    val heartRate: Color,
    val steps: Color,
    val activity: Color
) {

    fun colorFor(type: SeriesType): Color = when (type) {
        SeriesType.IOB             -> iob
        SeriesType.ABS_IOB         -> absIob
        SeriesType.COB             -> cob
        SeriesType.BGI             -> bgi
        SeriesType.DEVIATIONS      -> deviations
        SeriesType.SENSITIVITY     -> sensitivity
        SeriesType.VAR_SENSITIVITY -> varSensitivity
        SeriesType.DEV_SLOPE       -> devSlope
        SeriesType.HEART_RATE      -> heartRate
        SeriesType.STEPS           -> steps
        SeriesType.ACTIVITY        -> activity
    }
}

@Composable
fun rememberSeriesColors(): SeriesColors {
    val iobColor = AapsTheme.generalColors.iobPrediction
    val cobColor = AapsTheme.generalColors.cobPrediction
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(iobColor, cobColor, onSurface) {
        SeriesColors(
            iob = iobColor,                         // #1e88e5 blue (matches @color/iob)
            absIob = iobColor,                      // same blue as IOB
            cob = cobColor,                         // #FB8C00 orange (matches @color/cob)
            bgi = Color(0xFF00EEEE),                // cyan (matches @color/bgi)
            deviations = Color(0xFF00EEEE),         // cyan (matches @color/bgi — legacy uses per-type colors)
            sensitivity = onSurface,                // #000000 light / #FFFFFF dark (matches @color/ratio)
            varSensitivity = onSurface,             // same as sensitivity
            devSlope = Color(0xFFFFFF00),            // yellow (matches @color/devSlopePos)
            heartRate = Color(0xFFFFFF66),           // pale yellow (matches @color/heartRate #FFFFFF66)
            steps = Color(0xFF66FFB8),              // mint green (matches @color/steps)
            activity = Color(0xFFD3F166)            // lime green (matches @color/activity)
        )
    }
}

/** DevSlope min line — magenta, no fill (matches @color/devSlopeNeg #FF00FF) */
private fun createDevSlopeMinLine(): LineCartesianLayer.Line {
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color(0xFFFF00FF))),
        areaFill = null
    )
}

/** Create a line style for a given series type, matching legacy rendering */
fun createSeriesLine(type: SeriesType, colors: SeriesColors): LineCartesianLayer.Line {
    val color = colors.colorFor(type)
    return when (type) {
        // Step function with area fill (like IOB)
        SeriesType.ABS_IOB                                                       -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            ),
            pointConnector = Square
        )
        // Line only, no fill
        SeriesType.DEV_SLOPE, SeriesType.SENSITIVITY, SeriesType.VAR_SENSITIVITY -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = null
        )
        // Points/dots only — no connecting line
        SeriesType.HEART_RATE, SeriesType.STEPS                                  -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(color), shape = CircleShape),
                    size = 4.dp
                )
            )
        )
        // Default: smooth line with gradient area fill
        else                                                                     -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            )
        )
    }
}

// =========================================================================
// IOB line styles (bolus markers)
// =========================================================================

data class IobLineStyles(
    val iobLine: LineCartesianLayer.Line,
    val smallSmbLine: LineCartesianLayer.Line,
    val mediumSmbLine: LineCartesianLayer.Line,
    val largeSmbLine: LineCartesianLayer.Line,
    val bolusLine: LineCartesianLayer.Line,
    val extBolusLine: LineCartesianLayer.Line
)

@Composable
fun rememberIobLineStyles(): IobLineStyles {
    val iobColor = AapsTheme.generalColors.iobPrediction
    val smbColor = AapsTheme.elementColors.insulin
    val extBolusColor = AapsTheme.elementColors.extendedBolus

    val bolusLabelComponent = remember(smbColor) {
        TextComponent(textStyle = TextStyle(color = smbColor, fontSize = 16.sp))
    }
    val bolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value) }
    }
    val extBolusLabelComponent = remember(extBolusColor) {
        TextComponent(textStyle = TextStyle(color = extBolusColor, fontSize = 16.sp))
    }
    val extBolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value) }
    }

    return remember(iobColor, smbColor, extBolusColor, bolusLabelComponent, bolusValueFormatter, extBolusLabelComponent, extBolusValueFormatter) {
        IobLineStyles(
            iobLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(iobColor)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(Brush.verticalGradient(listOf(iobColor.copy(alpha = 1f), Color.Transparent)))
                ),
                pointConnector = Square
            ),
            smallSmbLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = 10.dp)
                )
            ),
            mediumSmbLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = 16.dp)
                )
            ),
            largeSmbLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = 22.dp)
                )
            ),
            bolusLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = InvertedTriangleShape), size = 22.dp)
                ),
                dataLabel = bolusLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = bolusValueFormatter
            ),
            extBolusLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(extBolusColor)),
                areaFill = null,
                dataLabel = extBolusLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = extBolusValueFormatter
            )
        )
    }
}

// =========================================================================
// COB line styles (carbs markers)
// =========================================================================

data class CobLineStyles(
    val cobLine: LineCartesianLayer.Line,
    val failoverDotsLine: LineCartesianLayer.Line,
    val carbsLine: LineCartesianLayer.Line
)

@Composable
fun rememberCobLineStyles(): CobLineStyles {
    val cobColor = AapsTheme.generalColors.cobPrediction
    val carbsColor = AapsTheme.elementColors.carbs

    val carbsLabelComponent = remember(carbsColor) {
        TextComponent(textStyle = TextStyle(color = carbsColor, fontSize = 14.sp))
    }
    val carbsValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatCarbsLabel(value) }
    }

    return remember(cobColor, carbsColor, carbsLabelComponent, carbsValueFormatter) {
        CobLineStyles(
            cobLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(cobColor)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(Brush.verticalGradient(listOf(cobColor.copy(alpha = 1f), Color.Transparent)))
                ),
                pointConnector = AdaptiveStep
            ),
            failoverDotsLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(cobColor), shape = CircleShape), size = 6.dp)
                )
            ),
            carbsLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(carbsColor), shape = InvertedTriangleShape), size = 22.dp)
                ),
                dataLabel = carbsLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = carbsValueFormatter
            )
        )
    }
}

// =========================================================================
// Label formatters (shared with IOB/COB graph composables)
// =========================================================================

/** Formats bolus amount: 1.0->"1", 1.2->"1.2", 0.8->".8" (drop leading zero) */
private fun formatBolusLabel(value: Double): String {
    if (value == 0.0) return ""
    val formatted = "%.2f".format(value).trimEnd('0').trimEnd('.').trimEnd(',')
    return if (formatted.startsWith("0.")) formatted.substring(1) else formatted
}

/** Formats carbs amount: 45.0->"45", 7.5->"7.5", 0.0->"" */
private fun formatCarbsLabel(value: Double): String {
    if (value == 0.0) return ""
    return "%.1f".format(value).trimEnd('0').trimEnd('.').trimEnd(',')
}

// =========================================================================
// Deviation column colors (match legacy @color/deviation* values)
// =========================================================================

private val DEVIATION_COLOR_POSITIVE = Color(0xC000FF00) // green (matches @color/deviationGreen)
private val DEVIATION_COLOR_NEGATIVE = Color(0xC0FF0000) // red (matches @color/deviationRed)
private val DEVIATION_COLOR_EQUAL = Color(0x72000000)    // black (matches @color/deviationBlack)
private val DEVIATION_COLOR_UAM = Color(0xFFC9BD60)      // yellow (matches @color/uam)
private val DEVIATION_COLOR_CSF = Color(0xC8666666)      // grey (matches @color/deviationGrey)

/**
 * Thread-safe holder for deviation type lookup map.
 * Populated in LaunchedEffect (composition thread), read by ColumnProvider.getColumn() (render thread).
 */
private class DeviationTypeLookup {

    @Volatile var map: Map<Double, DeviationType> = emptyMap()
}

