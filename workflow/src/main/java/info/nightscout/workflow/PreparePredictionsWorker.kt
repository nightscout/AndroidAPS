package info.nightscout.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.graph.data.DataPointWithLabelInterface
import info.nightscout.core.graph.data.GlucoseValueDataPoint
import info.nightscout.core.graph.data.PointsWithLabelGraphSeries
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.overview.OverviewMenus
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.T
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class PreparePredictionsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var config: Config
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var loop: Loop
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class PreparePredictionsData(
        val overviewData: OverviewData
    )

    override fun doWork(): Result {
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PreparePredictionsData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val apsResult = if (config.APS) loop.lastRun?.constraintsProcessed else processedDeviceStatusData.getAPSResult(injector)
        val predictionsAvailable = if (config.APS) loop.lastRun?.request?.hasPredictions == true else config.NSCLIENT
        val menuChartSettings = overviewMenus.setting
        // align to hours
        val calendar = Calendar.getInstance().also {
            it.timeInMillis = System.currentTimeMillis()
            it[Calendar.MILLISECOND] = 0
            it[Calendar.SECOND] = 0
            it[Calendar.MINUTE] = 0
            it.add(Calendar.HOUR, 1)
        }
        if (predictionsAvailable && apsResult != null && menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal]) {
            var predictionHours = (ceil(apsResult.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt()
            predictionHours = min(2, predictionHours)
            predictionHours = max(0, predictionHours)
            val hoursToFetch = data.overviewData.rangeToDisplay - predictionHours
            data.overviewData.toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(hoursToFetch.toLong()).msecs()
            data.overviewData.endTime = data.overviewData.toTime + T.hours(predictionHours.toLong()).msecs()
        } else {
            data.overviewData.toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(data.overviewData.rangeToDisplay.toLong()).msecs()
            data.overviewData.endTime = data.overviewData.toTime
        }

        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        val predictions: MutableList<GlucoseValueDataPoint>? = apsResult?.predictions
            ?.map { bg -> GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh) }
            ?.toMutableList()
        if (predictions != null) {
            predictions.sortWith { o1: GlucoseValueDataPoint, o2: GlucoseValueDataPoint -> o1.x.compareTo(o2.x) }
            for (prediction in predictions) if (prediction.data.value >= 40) bgListArray.add(prediction)
        }
        data.overviewData.predictionsGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        return Result.success()
    }
}