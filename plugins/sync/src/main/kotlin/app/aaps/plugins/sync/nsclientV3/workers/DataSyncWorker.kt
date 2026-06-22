package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val dataSyncSelectorV3: DataSyncSelectorV3,
    private val activePlugin: ActivePlugin,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientRepository: NSClientRepository
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        if (nsClientV3Plugin.doingFullSync) {
            nsClientRepository.addLog("● RUN", "Full sync finished")
            nsClientV3Plugin.endFullSync()
        }
        if (nsClientV3Plugin.hasWritePermission || nsClientV3Plugin.nsClientV3Service?.wsConnected == true) {
            nsClientRepository.addLog("► UPL", "Start")
            try {
                // Hard cap so a hung HTTP call / dead WS can't keep the worker in
                // RUNNING/BLOCKED forever and silently block every future upload.
                withTimeout(UPLOAD_TIMEOUT_MS) { dataSyncSelectorV3.doUpload() }
                nsClientRepository.addLog("► UPL", "End")
            } catch (e: TimeoutCancellationException) {
                nsClientRepository.addLog("◄ ERROR", "Upload timed out")
                aapsLogger.error(LTag.NSCLIENT, "DataSyncWorker timed out", e)
                return Result.failure(workDataOf("Error" to "Upload timed out"))
            }
        } else {
            if (nsClientV3Plugin.hasWritePermission)
                nsClientRepository.addLog("► ERROR", "No write permission")
            else if (nsClientV3Plugin.nsClientV3Service?.wsConnected == true)
                nsClientRepository.addLog("► ERROR", "Not connected")
            // refresh token
            nsClientV3Plugin.scheduleIrregularExecution(refreshToken = true)
        }
        return Result.success()
    }

    companion object {

        private val UPLOAD_TIMEOUT_MS = T.mins(30).msecs()
    }
}