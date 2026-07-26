package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers

@HiltWorker
class LoadStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientRepository: NSClientRepository
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        try {
            val status = nsAndroidClient.getStatus()
            aapsLogger.debug(LTag.NSCLIENT, "STATUS: $status")
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.localizedMessage)
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            nsClientRepository.updateStatus(nsClientV3Plugin.status)
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }
        nsClientV3Plugin.lastOperationError = null
        nsClientRepository.updateStatus(nsClientV3Plugin.status)
        return Result.success()
    }
}