package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.plugins.sync.nsclient.data.NSDeviceStatusHandler
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadDeviceStatusWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler

    override fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))
        var ret = Result.success()

        runBlocking {
            try {
                val from = dateUtil.now() - T.mins(7).msecs()
                val deviceStatuses = nsAndroidClient.getDeviceStatusModifiedSince(from)
                aapsLogger.debug("DEVICESTATUSES: $deviceStatuses")
                if (deviceStatuses.isNotEmpty()) {
                    rxBus.send(EventNSClientNewLog("RCV", "${deviceStatuses.size} DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}"))
                    nsDeviceStatusHandler.handleNewData(deviceStatuses.toTypedArray())
                    rxBus.send(EventNSClientNewLog("DONE DS", ""))
                } else {
                    rxBus.send(EventNSClientNewLog("RCV END", "No DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}"))
                }
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        NSClientV3Plugin.JOB_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequest.Builder(DataSyncWorker::class.java).build()
                    )
            } catch (error: Exception) {
                aapsLogger.error("Error: ", error)
                ret = Result.failure(workDataOf("Error" to error.toString()))
            }
        }
        return ret
    }
}