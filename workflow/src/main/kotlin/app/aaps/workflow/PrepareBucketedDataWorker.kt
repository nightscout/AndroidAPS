package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.profile.ProfileUtil
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

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences

    class PrepareBucketedData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData,
        val cache: OverviewDataCache
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as? PrepareBucketedData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val bucketedData = data.iobCobCalculator.ads.getBucketedDataTableCopy() ?: return Result.success()
        if (bucketedData.isEmpty()) {
            aapsLogger.debug("No bucketed data.")
            return Result.success()
        }

        // Refresh the 24h window so history navigation doesn't inherit a stale range.
        val newToTime = data.overviewData.toTime
        val newFromTime = newToTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
        data.cache.updateTimeRange(
            TimeRange(
                fromTime = newFromTime,
                toTime = newToTime,
                endTime = newToTime
            )
        )

        val veryHighMark = preferences.get(UnitDoubleKey.OverviewVeryHighMark)
        val highMark = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
        val veryLowMark = preferences.get(UnitDoubleKey.OverviewVeryLowMark)

        val bucketedDataPoints = bucketedData
            .filter { it.timestamp in newFromTime..newToTime }
            .map { inMemoryGlucoseValue ->
                // Use recalculated (smoothed value with fallback to original)
                val valueInUnits = profileUtil.fromMgdlToUnits(inMemoryGlucoseValue.recalculated)
                val range = when {
                    valueInUnits > veryHighMark -> BgRange.VERYHIGH
                    valueInUnits > highMark     -> BgRange.HIGH
                    valueInUnits < veryLowMark  -> BgRange.VERYLOW
                    valueInUnits < lowMark      -> BgRange.LOW
                    else                        -> BgRange.IN_RANGE
                }
                BgDataPoint(
                    timestamp = inMemoryGlucoseValue.timestamp,
                    value = valueInUnits,
                    range = range,
                    type = BgType.BUCKETED,
                    filledGap = inMemoryGlucoseValue.filledGap
                )
            }

        data.cache.updateBucketedData(bucketedDataPoints)

        return Result.success()
    }
}
