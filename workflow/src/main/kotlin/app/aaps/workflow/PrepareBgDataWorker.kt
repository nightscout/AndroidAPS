package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
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

class PrepareBgDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences

    class PrepareBgData(
        val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
        val overviewData: OverviewData,
        val cache: OverviewDataCache
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val toTime = data.overviewData.toTime
        val fromTime = toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()

        val bgReadingsArray = persistenceLayer.getBgReadingsDataFromTimeToTime(fromTime, toTime, false)

        val veryHighMarkInUnits = preferences.get(UnitDoubleKey.OverviewVeryHighMark)
        val highMarkInUnits = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMarkInUnits = preferences.get(UnitDoubleKey.OverviewLowMark)
        val veryLowMarkInUnits = preferences.get(UnitDoubleKey.OverviewVeryLowMark)

        val bgDataPoints = bgReadingsArray
            .filter { it.timestamp in fromTime..toTime }
            .map { bg ->
                val valueInUnits = profileUtil.fromMgdlToUnits(bg.value)
                BgDataPoint(
                    timestamp = bg.timestamp,
                    value = valueInUnits,
                    range = when {
                        valueInUnits > veryHighMarkInUnits -> BgRange.VERYHIGH
                        valueInUnits > highMarkInUnits     -> BgRange.HIGH
                        valueInUnits < veryLowMarkInUnits  -> BgRange.VERYLOW
                        valueInUnits < lowMarkInUnits      -> BgRange.LOW
                        else                               -> BgRange.IN_RANGE
                    },
                    type = BgType.REGULAR
                )
            }

        data.cache.updateTimeRange(
            TimeRange(
                fromTime = fromTime,
                toTime = toTime,
                endTime = toTime
            )
        )
        data.cache.updateBgReadings(bgDataPoints)

        return Result.success()
    }
}
