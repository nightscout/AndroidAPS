package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.plugins.sync.nsclient.data.NSDeviceStatusHandler
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadDeviceStatusWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler

    override fun doWork(): Result {
        var ret = Result.success()

        runBlocking {
            try {
                val from = dateUtil.now() - T.mins(7).msecs()
                val deviceStatuses = nsClientV3Plugin.nsAndroidClient.getDeviceStatusModifiedSince(from)
                aapsLogger.debug("DEVICESTATUSES: $deviceStatuses")
                if (deviceStatuses.isNotEmpty()) {
                    rxBus.send(EventNSClientNewLog("RCV", "${deviceStatuses.size} DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}"))
                    nsDeviceStatusHandler.handleNewData(deviceStatuses.toTypedArray())
                } else {
                    rxBus.send(EventNSClientNewLog("END", "No DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}"))
                }
            } catch (error: Exception) {
                aapsLogger.error("Error: ", error)
                ret = Result.failure(workDataOf("Error" to error.toString()))
            }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}