package info.nightscout.plugins.sync.xdrip.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.annotations.OpenForTesting
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventXdripNewLog
import info.nightscout.plugins.sync.xdrip.DataSyncSelectorXdripImpl
import info.nightscout.plugins.sync.xdrip.events.EventXdripUpdateGUI
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