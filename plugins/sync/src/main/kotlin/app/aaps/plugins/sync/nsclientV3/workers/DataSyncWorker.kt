package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class DataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var nsClientRepository: NSClientRepository

    override suspend fun doWorkAndLog(): Result {
        if (nsClientV3Plugin.doingFullSync) {
            nsClientRepository.addLog("● RUN", "Full sync finished")
            nsClientV3Plugin.endFullSync()
        }
        if (activePlugin.activeNsClient?.hasWritePermission == true || nsClientV3Plugin.nsClientV3Service?.wsConnected == true) {
            nsClientRepository.addLog("► UPL", "Start")
            dataSyncSelectorV3.doUpload()
            nsClientRepository.addLog("► UPL", "End")
        } else {
            if (activePlugin.activeNsClient?.hasWritePermission == true)
                nsClientRepository.addLog("► ERROR", "No write permission")
            else if (nsClientV3Plugin.nsClientV3Service?.wsConnected == true)
                nsClientRepository.addLog("► ERROR", "Not connected")
            // refresh token
            nsClientV3Plugin.scheduleIrregularExecution(refreshToken = true)
        }
        return Result.success()
    }
}