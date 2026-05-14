package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.iob.IobCobCalculator
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
import app.aaps.core.objects.workflow.LoggingWorker
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import javax.inject.Inject
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
class PostCalculationWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var workflowChainData: WorkflowChainData
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var config: Config
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences

    class PostCalculationData(
        val overviewData: OverviewData,
        val cache: OverviewDataCache,
        val signals: CalculationSignalsEmitter,
        val triggeredByNewBG: Boolean,
        val runLoopAndWidgetPhase: Boolean
    )

    override suspend fun doWorkAndLog(): Result {
        val data = workflowChainData.postFor(
            inputData.getString(WorkflowChainData.JOB_KEY),
            inputData.getLong(WorkflowChainData.GEN_KEY, -1L)
        ) ?: return Result.failure(workDataOf("Error" to "missing or stale input data"))

        if (data.runLoopAndWidgetPhase) {
            invokeLoop(data)
            if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))
            widgetUpdater.update("WorkFlow")
            if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))
        }

        preparePredictions(data)
        if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))

        data.signals.emitProgress(CalculationWorkflow.ProgressData.DRAW_FINAL, 100)
        return Result.success()
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
        // align to hours
        val calendar = Calendar.getInstance().also {
            it.timeInMillis = System.currentTimeMillis()
            it[Calendar.MILLISECOND] = 0
            it[Calendar.SECOND] = 0
            it[Calendar.MINUTE] = 0
            it.add(Calendar.HOUR, 1)
        }
        if (predictionsAvailable && apsResult != null) {
            var predictionHours = (ceil(apsResult.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt()
            predictionHours = min(2, predictionHours)
            predictionHours = max(0, predictionHours)
            val hoursToFetch = Constants.GRAPH_TIME_RANGE_HOURS - predictionHours
            data.overviewData.toTime = calendar.timeInMillis + 100000 // GraphView-era nudge, retained while workers still consume this shape
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(hoursToFetch.toLong()).msecs()
            data.overviewData.endTime = data.overviewData.toTime + T.hours(predictionHours.toLong()).msecs()
        } else {
            data.overviewData.toTime = calendar.timeInMillis + 100000
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
