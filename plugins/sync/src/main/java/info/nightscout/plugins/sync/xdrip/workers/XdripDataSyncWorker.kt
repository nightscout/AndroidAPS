package info.nightscout.plugins.sync.xdrip.workers

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.plugins.sync.xdrip.DataSyncSelectorXdripImpl
import info.nightscout.plugins.sync.xdrip.events.EventXdripUpdateGUI
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventXdripNewLog
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@OpenForTesting
class XdripDataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataSyncSelector: DataSyncSelectorXdripImpl
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus

    override suspend fun doWorkAndLog(): Result {
        rxBus.send(EventXdripNewLog("UPL", "Start"))
        dataSyncSelector.doUpload()
        rxBus.send(EventXdripNewLog("UPL", "End"))
        rxBus.send(EventXdripUpdateGUI())
        return Result.success()
    }
}