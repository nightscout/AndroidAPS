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
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.Round
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class PrepareBgDataWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var repository: AppRepository

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class PrepareBgData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData
    )

    override fun doWork(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        data.overviewData.maxBgValue = Double.MIN_VALUE
        data.overviewData.bgReadingsArray = repository.compatGetBgReadingsDataFromTime(data.overviewData.fromTime, data.overviewData.toTime, false).blockingGet()
        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (bg in data.overviewData.bgReadingsArray) {
            if (bg.timestamp < data.overviewData.fromTime || bg.timestamp > data.overviewData.toTime) continue
            if (bg.value > data.overviewData.maxBgValue) data.overviewData.maxBgValue = bg.value
            bgListArray.add(GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh))
        }
        bgListArray.sortWith { o1: DataPointWithLabelInterface, o2: DataPointWithLabelInterface -> o1.x.compareTo(o2.x) }
        data.overviewData.bgReadingGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        data.overviewData.maxBgValue = Profile.fromMgdlToUnits(data.overviewData.maxBgValue, profileFunction.getUnits())
        if (defaultValueHelper.determineHighLine() > data.overviewData.maxBgValue) data.overviewData.maxBgValue = defaultValueHelper.determineHighLine()
        data.overviewData.maxBgValue = addUpperChartMargin(data.overviewData.maxBgValue)
        return Result.success()
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileFunction.getUnits() == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4
}