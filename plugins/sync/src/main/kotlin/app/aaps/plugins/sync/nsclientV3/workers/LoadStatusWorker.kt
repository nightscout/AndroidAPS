package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LoadStatusWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var rxBus: RxBus

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        try {
            val status = nsAndroidClient.getStatus()
            aapsLogger.debug(LTag.NSCLIENT, "STATUS: $status")
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            rxBus.send(EventNSClientUpdateGuiStatus())
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }
        nsClientV3Plugin.lastOperationError = null
        rxBus.send(EventNSClientUpdateGuiStatus())
        return Result.success()
    }
}