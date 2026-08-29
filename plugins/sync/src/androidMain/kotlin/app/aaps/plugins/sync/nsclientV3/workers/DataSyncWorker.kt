package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.WorkerInstanceFactory
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
        if (nsClientV3Plugin.hasWritePermission || nsClientV3Plugin.wsConnectedFlow.value) {
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
            // Both conditions are negated here. They used to be written the same way round as the
            // `if` above, which made them unreachable: this is the else of `hasWritePermission ||
            // connected`, so neither could ever be true and neither message was ever logged.
            if (!nsClientV3Plugin.hasWritePermission)
                nsClientRepository.addLog("► ERROR", "No write permission")
            else
                nsClientRepository.addLog("► ERROR", "Not connected")
            // refresh token
            nsClientV3Plugin.scheduleIrregularExecution(refreshToken = true)
        }
        return Result.success()
    }

    companion object {

        private val UPLOAD_TIMEOUT_MS = T.mins(30).msecs()
    }

    /** Metro builds the worker through this - WorkManager supplies context and params. */
    @AssistedFactory
    abstract class Factory : WorkerInstanceFactory<DataSyncWorker>()
}
