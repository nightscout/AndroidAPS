package app.aaps.plugins.sync.xdrip.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class XdripDataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataSyncSelector: DataSyncSelectorXdrip
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var xdripMvvmRepository: XdripMvvmRepository

    override suspend fun doWorkAndLog(): Result {
        xdripMvvmRepository.addLog("UPL", "Start")
        dataSyncSelector.doUpload()
        xdripMvvmRepository.addLog("UPL", "End")
        xdripMvvmRepository.updateQueueSize(dataSyncSelector.queueSize())
        return Result.success()
    }
}
