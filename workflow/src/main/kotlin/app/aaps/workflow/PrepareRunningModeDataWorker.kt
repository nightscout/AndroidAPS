package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.RM
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.graph.data.RunningModeDataPoint
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.math.max

class PrepareRunningModeDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var loop: Loop
    @Inject lateinit var rxBus: RxBus

    class PrepareRunningModeData(
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareRunningModeData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_RUNNING_MODE_DATA, 0, null))
        var endTime = data.overviewData.endTime
        val fromTime = data.overviewData.fromTime
        val modesSeriesArray: MutableList<RunningModeDataPoint> = ArrayList()
        var lastMode = RM.Mode.RESUME
        var lastModeChange = fromTime
        loop.lastRun?.constraintsProcessed?.let { endTime = max(it.latestPredictionsTime, endTime) }
        var time = fromTime
        while (time < endTime) {
            if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))
            val progress = (time - fromTime).toDouble() / (endTime - fromTime) * 100.0
            rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_RUNNING_MODE_DATA, progress.toInt(), null))
            val mode = persistenceLayer.getRunningModeActiveAt(time)
            if (lastMode != mode.mode) {
                if (lastMode != RM.Mode.RESUME)
                    modesSeriesArray.add(RunningModeDataPoint(lastMode, lastModeChange, time, rh))
                lastModeChange = time
                lastMode = mode.mode
            }
            time += 5 * 60 * 1000L
        }
        modesSeriesArray.add(RunningModeDataPoint(lastMode, lastModeChange, time, rh))
        // create series
        data.overviewData.runningModesSeries = PointsWithLabelGraphSeries(Array(modesSeriesArray.size) { i -> modesSeriesArray[i] })
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_RUNNING_MODE_DATA, 100, null))
        return Result.success()
    }
}