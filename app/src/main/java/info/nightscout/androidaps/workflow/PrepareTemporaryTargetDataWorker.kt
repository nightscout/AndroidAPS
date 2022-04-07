package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.extensions.target
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import kotlin.math.max

class PrepareTemporaryTargetDataWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var loop: Loop
    @Inject lateinit var rxBus: RxBus

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class PrepareTemporaryTargetData(
        val overviewData: OverviewData
    )

    override fun doWork(): Result {

        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as PrepareTemporaryTargetData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TEMPORARY_TARGET_DATA, 0, null))
        val profile = profileFunction.getProfile() ?: return Result.failure(workDataOf("Error" to "missing profile"))
        val units = profileFunction.getUnits()
        var toTime = data.overviewData.toTime
        val targetsSeriesArray: MutableList<DataPoint> = ArrayList()
        var lastTarget = -1.0
        loop.lastRun?.constraintsProcessed?.let { toTime = max(it.latestPredictionsTime, toTime) }
        var time = data.overviewData.fromTime
        while (time < toTime) {
            val progress = (time - data.overviewData.fromTime).toDouble() / (data.overviewData.toTime - data.overviewData.fromTime) * 100.0
            rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TEMPORARY_TARGET_DATA, progress.toInt(), null))
            val tt = repository.getTemporaryTargetActiveAt(time).blockingGet()
            val value: Double = if (tt is ValueWrapper.Existing) {
                Profile.fromMgdlToUnits(tt.value.target(), units)
            } else {
                Profile.fromMgdlToUnits((profile.getTargetLowMgdl(time) + profile.getTargetHighMgdl(time)) / 2, units)
            }
            if (lastTarget != value) {
                if (lastTarget != -1.0) targetsSeriesArray.add(DataPoint(time.toDouble(), lastTarget))
                targetsSeriesArray.add(DataPoint(time.toDouble(), value))
            }
            lastTarget = value
            time += 5 * 60 * 1000L
        }
        // final point
        targetsSeriesArray.add(DataPoint(toTime.toDouble(), lastTarget))
        // create series
        data.overviewData.temporaryTargetSeries = LineGraphSeries(Array(targetsSeriesArray.size) { i -> targetsSeriesArray[i] }).also {
            it.isDrawBackground = false
            it.color = rh.gc(R.color.tempTargetBackground)
            it.thickness = 2
        }
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.PREPARE_TEMPORARY_TARGET_DATA, 100, null))
        return Result.success()
    }
}