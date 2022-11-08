package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.OverviewMenus
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.GlucoseValueDataPoint
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
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
    @Inject lateinit var nsDeviceStatus: NSDeviceStatus
    @Inject lateinit var loop: Loop
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class PreparePredictionsData(
        val overviewData: OverviewData
    )

    override fun doWork(): Result {
        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as PreparePredictionsData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val apsResult = if (config.APS) loop.lastRun?.constraintsProcessed else nsDeviceStatus.getAPSResult(injector)
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
            ?.map { bg -> GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh, sp) }
            ?.toMutableList()
        if (predictions != null) {
            predictions.sortWith { o1: GlucoseValueDataPoint, o2: GlucoseValueDataPoint -> o1.x.compareTo(o2.x) }
            for (prediction in predictions) if (prediction.data.value >= 40) bgListArray.add(prediction)
        }
        data.overviewData.predictionsGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        return Result.success()
    }
}