package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventBucketedDataCreated
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LoadBgDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin

    class LoadBgData(
        val iobCobCalculator: IobCobCalculator,
        val end: Long
    )


    private fun AutosensDataStore.loadBgData(to: Long, repository: AppRepository, aapsLogger: AAPSLogger, dateUtil: DateUtil) {
        synchronized(dataLock) {
            val start = to - T.hours((24 + 10 /* max dia */).toLong()).msecs()
            // there can be some readings with time in close future (caused by wrong time setting on sensor)
            // so add 2 minutes
            bgReadings = repository
                .compatGetBgReadingsDataFromTime(start, to + T.mins(2).msecs(), false)
                .blockingGet()
            aapsLogger.debug(LTag.AUTOSENS) { "BG data loaded. Size: ${bgReadings.size} Start date: ${dateUtil.dateAndTimeString(start)} End date: ${dateUtil.dateAndTimeString(to)}" }
            createBucketedData(aapsLogger, dateUtil)
        }
    }

    private fun AutosensDataStore.smoothData(activePlugin: ActivePlugin) {
        synchronized(dataLock) {
            bucketedData?.let {
                val smoothedData = activePlugin.activeSmoothing.smooth(it)
                bucketedData = smoothedData
            }
        }
    }

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as LoadBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        data.iobCobCalculator.ads.loadBgData(data.end, repository, aapsLogger, dateUtil)
        data.iobCobCalculator.ads.smoothData(activePlugin)
        rxBus.send(EventBucketedDataCreated())
        data.iobCobCalculator.clearCache()
        return Result.success()
    }
}