package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.time.T
import app.aaps.core.graph.data.DataPointWithLabelInterface
import app.aaps.core.graph.data.GlucoseValueDataPoint
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class PreparePredictionsWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var config: Config
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var loop: Loop
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    class PreparePredictionsData(
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PreparePredictionsData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val apsResult = if (config.APS) loop.lastRun?.constraintsProcessed else processedDeviceStatusData.getAPSResult()
        val predictionsAvailable = if (config.APS) loop.lastRun?.request?.hasPredictions == true else config.AAPSCLIENT
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
        val predictions: MutableList<GlucoseValueDataPoint>? = apsResult?.predictionsAsGv
            ?.map { bg -> GlucoseValueDataPoint(bg, profileUtil, rh, dateUtil) }
            ?.toMutableList()
        if (predictions != null) {
            predictions.sortWith { o1: GlucoseValueDataPoint, o2: GlucoseValueDataPoint -> o1.x.compareTo(o2.x) }
            for (prediction in predictions) if (prediction.data.value >= 40) bgListArray.add(prediction)
        }
        data.overviewData.predictionsGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        return Result.success()
    }
}