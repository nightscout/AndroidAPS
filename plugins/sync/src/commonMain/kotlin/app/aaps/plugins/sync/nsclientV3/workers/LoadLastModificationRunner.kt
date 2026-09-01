package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository

import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dev.zacsweers.metro.Inject

/**
 * Asks Nightscout when each collection last changed, and records it on the plugin as
 * `newestDataOnServer`. The load chain uses that to decide what is worth fetching.
 */
@Inject
class LoadLastModificationRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsClientRepository: NSClientRepository
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")

        try {
            val lm = nsAndroidClient.getLastModified()
            nsClientV3Plugin.newestDataOnServer = lm
            aapsLogger.debug(LTag.NSCLIENT, "LAST MODIFIED: ${nsClientV3Plugin.newestDataOnServer}")
        } catch (error: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }
        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }
}
