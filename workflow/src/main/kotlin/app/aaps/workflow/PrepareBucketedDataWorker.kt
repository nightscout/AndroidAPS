package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.graph.data.DataPointWithLabelInterface
import app.aaps.core.graph.data.InMemoryGlucoseValueDataPoint
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class PrepareBucketedDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    // MIGRATION: KEEP - Core dependencies needed for calculation
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil

    // MIGRATION: KEEP - New cache for Compose graphs
    @Inject lateinit var overviewDataCache: OverviewDataCache

    // MIGRATION: DELETE - Remove after OverviewFragment converted to Compose
    class PrepareBucketedData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData // DELETE: This parameter goes away
    )

    override suspend fun doWorkAndLog(): Result {

        // MIGRATION: KEEP - Data retrieval logic
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as? PrepareBucketedData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        // MIGRATION: DELETE - Get time range from old OverviewData
        val toTime = data.overviewData.toTime
        val fromTime = data.overviewData.fromTime
        // MIGRATION: KEEP (replace with) - After cleanup, get from new cache:
        // val toTime = overviewDataCache.timeRange?.toTime ?: return Result.failure()
        // val fromTime = overviewDataCache.timeRange?.fromTime ?: return Result.failure()

        // MIGRATION: KEEP - Get bucketed data from IobCobCalculator
        val bucketedData = data.iobCobCalculator.ads.getBucketedDataTableCopy() ?: return Result.success()
        if (bucketedData.isEmpty()) {
            aapsLogger.debug("No bucketed data.")
            return Result.success()
        }

        // ========== MIGRATION: DELETE - Start GraphView-specific code ==========
        val bucketedListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (inMemoryGlucoseValue in bucketedData) {
            if (inMemoryGlucoseValue.timestamp !in fromTime..toTime) continue
            bucketedListArray.add(InMemoryGlucoseValueDataPoint(inMemoryGlucoseValue, preferences, profileFunction, rh))
        }
        bucketedListArray.sortWith { o1: DataPointWithLabelInterface, o2: DataPointWithLabelInterface -> o1.x.compareTo(o2.x) }
        data.overviewData.bucketedGraphSeries = PointsWithLabelGraphSeries(Array(bucketedListArray.size) { i -> bucketedListArray[i] })
        // ========== MIGRATION: DELETE - End GraphView-specific code ==========

        // ========== MIGRATION: KEEP - Start Compose/Vico code ==========
        // Set 24h time range in cache if not already set (this worker runs first in chain)
        if (overviewDataCache.timeRangeFlow.value == null) {
            val toTimeNew = toTime
            val fromTimeNew = toTimeNew - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
            overviewDataCache.updateTimeRange(
                TimeRange(
                    fromTime = fromTimeNew,
                    toTime = toTimeNew,
                    endTime = toTimeNew
                )
            )
        }
        val currentTimeRange = overviewDataCache.timeRangeFlow.value!!
        val newFromTime = currentTimeRange.fromTime
        val newToTime = currentTimeRange.toTime

        // Pre-compute thresholds once (not per-point)
        val highMark = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)

        val bucketedDataPoints = bucketedData
            .filter { it.timestamp in newFromTime..newToTime }
            .map { inMemoryGlucoseValue ->
                // Use recalculated (smoothed value with fallback to original)
                val valueInUnits = profileUtil.fromMgdlToUnits(inMemoryGlucoseValue.recalculated)
                val range = when {
                    valueInUnits > highMark -> BgRange.HIGH
                    valueInUnits < lowMark  -> BgRange.LOW
                    else                    -> BgRange.IN_RANGE
                }
                BgDataPoint(
                    timestamp = inMemoryGlucoseValue.timestamp,
                    value = valueInUnits,
                    range = range,
                    type = BgType.BUCKETED,
                    filledGap = inMemoryGlucoseValue.filledGap
                    // source = null (default)
                )
            }

        overviewDataCache.updateBucketedData(bucketedDataPoints)
        // ========== MIGRATION: KEEP - End Compose/Vico code ==========

        return Result.success()
    }
}