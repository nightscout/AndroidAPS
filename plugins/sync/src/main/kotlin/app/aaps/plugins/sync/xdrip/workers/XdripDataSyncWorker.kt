package app.aaps.plugins.sync.xdrip.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.xdrip.events.EventXdripNewLog
import app.aaps.plugins.sync.xdrip.events.EventXdripUpdateGUI
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class XdripDataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataSyncSelector: DataSyncSelectorXdrip
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