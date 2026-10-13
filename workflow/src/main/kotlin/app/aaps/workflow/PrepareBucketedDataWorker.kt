package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.graph.data.DataPointWithLabelInterface
import app.aaps.core.graph.data.InMemoryGlucoseValueDataPoint
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class PrepareBucketedDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences

    class PrepareBucketedData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBucketedData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val toTime = data.overviewData.toTime
        val fromTime = data.overviewData.fromTime
        val bucketedData = data.iobCobCalculator.ads.getBucketedDataTableCopy() ?: return Result.success()
        if (bucketedData.isEmpty()) {
            aapsLogger.debug("No bucketed data.")
            return Result.success()
        }
        val bucketedListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (inMemoryGlucoseValue in bucketedData) {
            if (inMemoryGlucoseValue.timestamp < fromTime || inMemoryGlucoseValue.timestamp > toTime) continue
            bucketedListArray.add(InMemoryGlucoseValueDataPoint(inMemoryGlucoseValue, preferences, profileFunction, rh))
        }
        bucketedListArray.sortWith { o1: DataPointWithLabelInterface, o2: DataPointWithLabelInterface -> o1.x.compareTo(o2.x) }
        data.overviewData.bucketedGraphSeries = PointsWithLabelGraphSeries(Array(bucketedListArray.size) { i -> bucketedListArray[i] })
        return Result.success()
    }
}