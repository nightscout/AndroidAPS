package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.extensions.rawOrSmoothed
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.GlucoseValueDataPoint
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.ArrayList
import javax.inject.Inject

class PrepareBgDataWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
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

        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as PrepareBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))
        val useSmoothed = sp.getBoolean(R.string.key_use_data_smoothing, false)

        data.overviewData.maxBgValue = Double.MIN_VALUE
        data.overviewData.bgReadingsArray = repository.compatGetBgReadingsDataFromTime(data.overviewData.fromTime, data.overviewData.toTime, false).blockingGet()
        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (bg in data.overviewData.bgReadingsArray) {
            if (bg.timestamp < data.overviewData.fromTime || bg.timestamp > data.overviewData.toTime) continue
            if (bg.rawOrSmoothed(useSmoothed) > data.overviewData.maxBgValue) data.overviewData.maxBgValue = bg.rawOrSmoothed(useSmoothed)
            bgListArray.add(GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh, sp))
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