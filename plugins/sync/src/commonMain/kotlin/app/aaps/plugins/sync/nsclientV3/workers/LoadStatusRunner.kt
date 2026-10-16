package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository

import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dev.zacsweers.metro.Inject

/**
 * Reads the Nightscout server status once and records the outcome on the plugin, so a failure shows
 * up in the client log and in the plugin's own status line rather than only being thrown away.
 */
@Inject
class LoadStatusRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientRepository: NSClientRepository
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")

        try {
            val status = nsAndroidClient.getStatus()
            aapsLogger.debug(LTag.NSCLIENT, "STATUS: $status")
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            nsClientRepository.updateStatus(nsClientV3Plugin.status)
            return WorkOutcome.Failure(error.message ?: "error")
        }
        nsClientV3Plugin.lastOperationError = null
        nsClientRepository.updateStatus(nsClientV3Plugin.status)
        return WorkOutcome.Success
    }
}
