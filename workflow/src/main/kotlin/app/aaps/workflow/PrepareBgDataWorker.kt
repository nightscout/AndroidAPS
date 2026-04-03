package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.time.T
import app.aaps.core.graph.data.DataPointWithLabelInterface
import app.aaps.core.graph.data.GlucoseValueDataPoint
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.math.min

class PrepareBgDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    // MIGRATION: KEEP - Core dependencies needed for calculation
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil

    // MIGRATION: KEEP - New cache for Compose graphs
    @Inject lateinit var overviewDataCache: OverviewDataCache

    // MIGRATION: DELETE - Remove after OverviewFragment converted to Compose
    class PrepareBgData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData // DELETE: This parameter goes away
    )

    override suspend fun doWorkAndLog(): Result {

        // MIGRATION: KEEP - Data retrieval logic
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        // MIGRATION: Get time range from OLD cache for OLD GraphView system (6h from user preference)
        val toTimeOld = data.overviewData.toTime
        val fromTimeOld = data.overviewData.fromTime

        // MIGRATION: Calculate 24h range for NEW Compose system
        val toTimeNew = toTimeOld  // Same end time
        val fromTimeNew = toTimeNew - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()

        // MIGRATION: Fetch data using MIN of both ranges (ensures we get 24h of data)
        val toTime = toTimeNew
        val fromTime = min(fromTimeOld, fromTimeNew)  // Furthest back

        // MIGRATION: KEEP - Fetch raw data from database
        val bgReadingsArray = persistenceLayer.getBgReadingsDataFromTimeToTime(fromTime, toTime, false)

        // MIGRATION: KEEP - Calculate max value
        var maxBgValue = Double.MIN_VALUE
        for (bg in bgReadingsArray) {
            if (bg.timestamp !in fromTime..toTime) continue
            if (bg.value > maxBgValue) maxBgValue = bg.value
        }

        // ========== MIGRATION: DELETE - Start GraphView-specific code ==========
        // Process data for OLD GraphView system using OLD time range
        data.overviewData.maxBgValue = Double.MIN_VALUE
        data.overviewData.bgReadingsArray = bgReadingsArray
        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (bg in bgReadingsArray) {
            if (bg.timestamp !in fromTimeOld..toTimeOld) continue
            if (bg.value > data.overviewData.maxBgValue) data.overviewData.maxBgValue = bg.value
            bgListArray.add(GlucoseValueDataPoint(bg, profileUtil, rh, dateUtil))
        }
        bgListArray.sortWith { o1: DataPointWithLabelInterface, o2: DataPointWithLabelInterface -> o1.x.compareTo(o2.x) }
        data.overviewData.bgReadingGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        data.overviewData.maxBgValue = profileUtil.fromMgdlToUnits(data.overviewData.maxBgValue)
        if (preferences.get(UnitDoubleKey.OverviewHighMark) > data.overviewData.maxBgValue)
            data.overviewData.maxBgValue = preferences.get(UnitDoubleKey.OverviewHighMark)
        data.overviewData.maxBgValue = addUpperChartMargin(data.overviewData.maxBgValue)
        // ========== MIGRATION: DELETE - End GraphView-specific code ==========

        // ========== MIGRATION: KEEP - Start Compose/Vico code ==========
        // Pre-compute threshold values (optimization: don't repeat for every BG reading)
        val highMarkInUnits = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMarkInUnits = preferences.get(UnitDoubleKey.OverviewLowMark)

        val bgDataPoints = bgReadingsArray
            .filter { it.timestamp in fromTime..toTime }
            .map { bg ->
                val valueInUnits = profileUtil.fromMgdlToUnits(bg.value)
                BgDataPoint(
                    timestamp = bg.timestamp,
                    value = valueInUnits,
                    range = when {
                        valueInUnits > highMarkInUnits -> BgRange.HIGH
                        valueInUnits < lowMarkInUnits  -> BgRange.LOW
                        else                           -> BgRange.IN_RANGE
                    },
                    type = BgType.REGULAR
                )
            }

        // Store time range in cache (observable by UI)
        overviewDataCache.updateTimeRange(
            TimeRange(
                fromTime = fromTimeNew,
                toTime = toTimeNew,
                endTime = toTimeNew
            )
        )

        // Update BG readings series independently
        overviewDataCache.updateBgReadings(bgDataPoints)
        // ========== MIGRATION: KEEP - End Compose/Vico code ==========

        // NOTE: BgInfo is now updated reactively by OverviewDataCacheImpl
        // which observes GlucoseValue database changes directly via Flow

        return Result.success()
    }

    // MIGRATION: KEEP - Helper function used by both old and new code
    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileUtil.units == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4
}