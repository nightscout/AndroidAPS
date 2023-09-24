package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.annotations.OpenForTesting
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import info.nightscout.plugins.sync.nsclientV3.DataSyncSelectorV3
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@OpenForTesting
class DataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin

    override suspend fun doWorkAndLog(): Result {
        if (activePlugin.activeNsClient?.hasWritePermission == true || nsClientV3Plugin.wsConnected) {
            rxBus.send(EventNSClientNewLog("► UPL", "Start"))
            dataSyncSelectorV3.doUpload()
            rxBus.send(EventNSClientNewLog("► UPL", "End"))
        } else {
            if (activePlugin.activeNsClient?.hasWritePermission == true)
                rxBus.send(EventNSClientNewLog("► ERROR", "No write permission"))
            else if (nsClientV3Plugin.wsConnected)
                rxBus.send(EventNSClientNewLog("► ERROR", "Not connected"))
            // refresh token
            nsClientV3Plugin.scheduleIrregularExecution(refreshToken = true)
        }
        return Result.success()
    }
}