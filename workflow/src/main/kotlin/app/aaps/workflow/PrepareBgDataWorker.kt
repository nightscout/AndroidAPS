package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.graph.data.DataPointWithLabelInterface
import app.aaps.core.main.graph.data.GlucoseValueDataPoint
import app.aaps.core.main.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.impl.AppRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class PrepareBgDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var repository: AppRepository

    class PrepareBgData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val toTime = data.overviewData.toTime
        val fromTime = data.overviewData.fromTime
        data.overviewData.maxBgValue = Double.MIN_VALUE
        data.overviewData.bgReadingsArray = repository.compatGetBgReadingsDataFromTime(fromTime, toTime, false).blockingGet()
        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (bg in data.overviewData.bgReadingsArray) {
            if (bg.timestamp < fromTime || bg.timestamp > toTime) continue
            if (bg.value > data.overviewData.maxBgValue) data.overviewData.maxBgValue = bg.value
            bgListArray.add(GlucoseValueDataPoint(bg, profileUtil, rh))
        }
        bgListArray.sortWith { o1: DataPointWithLabelInterface, o2: DataPointWithLabelInterface -> o1.x.compareTo(o2.x) }
        data.overviewData.bgReadingGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        data.overviewData.maxBgValue = profileUtil.fromMgdlToUnits(data.overviewData.maxBgValue)
        if (defaultValueHelper.determineHighLine() > data.overviewData.maxBgValue) data.overviewData.maxBgValue = defaultValueHelper.determineHighLine()
        data.overviewData.maxBgValue = addUpperChartMargin(data.overviewData.maxBgValue)
        return Result.success()
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileUtil.units == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4
}