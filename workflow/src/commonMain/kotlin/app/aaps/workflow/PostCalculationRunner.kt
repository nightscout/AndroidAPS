package app.aaps.workflow

import kotlin.time.Duration.Companion.hours
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDateTime
import dev.zacsweers.metro.Inject
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Merged tail-of-chain worker covering: APS loop invocation, widget update,
 * predictions prep, and the final DRAW_FINAL progress emit.
 *
 * Used by [CalculationWorkflow.runCalculation] (MAIN only, full phases) and by
 * [CalculationWorkflow.runOnReceivedPredictions] (predictions only).
 */
class PostCalculationRunner @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val workflowChainData: WorkflowChainData,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val widgetUpdater: WidgetUpdater,
    private val config: Config,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences
) {

    /** Runs the phase that follows the prepare pass. [isStopped] is asked between steps. */
    suspend fun run(job: String?, generation: Long, isStopped: () -> Boolean): WorkOutcome {
        val data = workflowChainData.postFor(job, generation) ?: return WorkOutcome.StaleInput

        if (data.runLoopAndWidgetPhase) {
            invokeLoop(data)
            if (isStopped()) return WorkOutcome.Stopped
            widgetUpdater.update("WorkFlow")
            if (isStopped()) return WorkOutcome.Stopped
        }

        preparePredictions(data)
        if (isStopped()) return WorkOutcome.Stopped

        data.signals.emitProgress(CalculationWorkflow.ProgressData.DRAW_FINAL, 100)
        return WorkOutcome.Success
    }

    /*
     * Triggered once autosens calculation has completed so the Loop has current data to work with.
     * Autosens can be triggered by multiple sources but currently only a new BG should trigger a loop run.
     */
    private suspend fun invokeLoop(data: PostCalculationData) {
        if (!data.triggeredByNewBG) return
        val glucoseValue = iobCobCalculator.ads.actualBg() ?: return
        if (glucoseValue.timestamp <= loop.lastBgTriggeredRun) return
        loop.lastBgTriggeredRun = glucoseValue.timestamp
        loop.invoke("Calculation for $glucoseValue", true)
    }

    private fun preparePredictions(data: PostCalculationData) {
        val apsResult = if (config.APS) loop.lastRun?.constraintsProcessed else processedDeviceStatusData.getAPSResult()
        val predictionsAvailable = if (config.APS) loop.lastRun?.request?.hasPredictions == true else config.AAPSCLIENT
        // Align to the next local hour boundary. Local, not UTC: zones like +05:30 do not line up
        // with UTC hours, and this is what the Calendar version did.
        val zone = TimeZone.currentSystemDefault()
        val nowLocal = Clock.System.now().toLocalDateTime(zone)
        val nextHour = LocalDateTime(nowLocal.year, nowLocal.month, nowLocal.day, nowLocal.hour, 0)
            .toInstant(zone) + 1.hours
        if (predictionsAvailable && apsResult != null) {
            var predictionHours = (ceil(apsResult.latestPredictionsTime - Clock.System.now().toEpochMilliseconds().toDouble()) / (60 * 60 * 1000)).toInt()
            predictionHours = min(2, predictionHours)
            predictionHours = max(0, predictionHours)
            val hoursToFetch = Constants.GRAPH_TIME_RANGE_HOURS - predictionHours
            data.overviewData.toTime = nextHour.toEpochMilliseconds() + 100000 // GraphView-era nudge, retained while workers still consume this shape
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(hoursToFetch.toLong()).msecs()
            data.overviewData.endTime = data.overviewData.toTime + T.hours(predictionHours.toLong()).msecs()
        } else {
            data.overviewData.toTime = nextHour.toEpochMilliseconds() + 100000
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
            data.overviewData.endTime = data.overviewData.toTime
        }

        val highMarkInUnits = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMarkInUnits = preferences.get(UnitDoubleKey.OverviewLowMark)

        val predictionDataPoints = apsResult?.predictionsAsGv
            ?.filter { it.value >= 40 }
            ?.map { gv ->
                val valueInUnits = profileUtil.fromMgdlToUnits(gv.value)
                BgDataPoint(
                    timestamp = gv.timestamp,
                    value = valueInUnits,
                    range = when {
                        valueInUnits > highMarkInUnits -> BgRange.HIGH
                        valueInUnits < lowMarkInUnits  -> BgRange.LOW
                        else                           -> BgRange.IN_RANGE
                    },
                    type = when (gv.sourceSensor) {
                        SourceSensor.IOB_PREDICTION   -> BgType.IOB_PREDICTION
                        SourceSensor.COB_PREDICTION   -> BgType.COB_PREDICTION
                        SourceSensor.A_COB_PREDICTION -> BgType.A_COB_PREDICTION
                        SourceSensor.UAM_PREDICTION   -> BgType.UAM_PREDICTION
                        SourceSensor.ZT_PREDICTION    -> BgType.ZT_PREDICTION
                        else                          -> BgType.IOB_PREDICTION
                    }
                )
            }
            ?.sortedBy { it.timestamp }
            ?: emptyList()

        data.cache.updatePredictions(predictionDataPoints)

        // Extend cached time range to include prediction horizon
        data.cache.timeRangeFlow.value?.let { current ->
            data.cache.updateTimeRange(current.copy(endTime = data.overviewData.endTime))
        }
    }

}
