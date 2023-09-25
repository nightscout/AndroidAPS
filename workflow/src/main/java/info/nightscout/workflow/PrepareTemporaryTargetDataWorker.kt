package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.main.events.EventIobCalculationProgress
import app.aaps.core.main.extensions.target
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.main.workflow.CalculationWorkflow
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.ValueWrapper
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.database.impl.AppRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.math.max

class PrepareTemporaryTargetDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var loop: Loop
    @Inject lateinit var rxBus: RxBus
    private var ctx: Context

    init {
        ctx = rh.getThemedCtx(context)
    }

    class PrepareTemporaryTargetData(
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareTemporaryTargetData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TEMPORARY_TARGET_DATA, 0, null))
        val profile = profileFunction.getProfile() ?: return Result.success(workDataOf("Error" to "missing profile"))
        var endTime = data.overviewData.endTime
        val fromTime = data.overviewData.fromTime
        val targetsSeriesArray: MutableList<DataPoint> = ArrayList()
        var lastTarget = -1.0
        loop.lastRun?.constraintsProcessed?.let { endTime = max(it.latestPredictionsTime, endTime) }
        var time = fromTime
        while (time < endTime) {
            if (isStopped) return Result.failure(workDataOf("Error" to "stopped"))
            val progress = (time - fromTime).toDouble() / (endTime - fromTime) * 100.0
            rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TEMPORARY_TARGET_DATA, progress.toInt(), null))
            val tt = repository.getTemporaryTargetActiveAt(time).blockingGet()
            val value: Double = if (tt is ValueWrapper.Existing) {
                profileUtil.fromMgdlToUnits(tt.value.target())
            } else {
                profileUtil.fromMgdlToUnits((profile.getTargetLowMgdl(time) + profile.getTargetHighMgdl(time)) / 2)
            }
            if (lastTarget != value) {
                if (lastTarget != -1.0) targetsSeriesArray.add(DataPoint(time.toDouble(), lastTarget))
                targetsSeriesArray.add(DataPoint(time.toDouble(), value))
            }
            lastTarget = value
            time += 5 * 60 * 1000L
        }
        // final point
        targetsSeriesArray.add(DataPoint(endTime.toDouble(), lastTarget))
        // create series
        data.overviewData.temporaryTargetSeries = LineGraphSeries(Array(targetsSeriesArray.size) { i -> targetsSeriesArray[i] }).also {
            it.isDrawBackground = false
            it.color = rh.gac(ctx, app.aaps.core.ui.R.attr.tempTargetBackgroundColor)
            it.thickness = 2
        }
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TEMPORARY_TARGET_DATA, 100, null))
        return Result.success()
    }
}