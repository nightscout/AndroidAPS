package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGUI
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@OpenForTesting
class DataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus

    override suspend fun doWorkAndLog(): Result {
        if (activePlugin.activeNsClient?.hasWritePermission == true) {
            rxBus.send(EventNSClientNewLog("UPL", "Start"))
            dataSyncSelector.doUpload()
            rxBus.send(EventNSClientNewLog("UPL", "End"))
        }
        rxBus.send(EventNSClientUpdateGUI())
        return Result.success()
    }
}