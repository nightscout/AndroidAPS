package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.utils.DateUtil

import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import dev.zacsweers.metro.Inject

@Inject
class LoadDeviceStatusRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val nsClientRepository: NSClientRepository
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")

        try {
            // Notify plugin we loaded al missed data
            nsClientV3Plugin.initialLoadFinished = true

            val from = dateUtil.now() - T.mins(7).msecs()
            val deviceStatuses = nsAndroidClient.getDeviceStatusModifiedSince(from)
            aapsLogger.debug("DEVICESTATUSES: $deviceStatuses")
            if (deviceStatuses.isNotEmpty()) {
                nsClientRepository.addLog("◄ RCV", "${deviceStatuses.size} DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}")
                nsDeviceStatusHandler.handleNewData(deviceStatuses.toTypedArray())
                nsClientRepository.addLog("● DONE PROCESSING DS", "")
            } else {
                nsClientRepository.addLog("◄ RCV DS END", "No data from ${dateUtil.dateAndTimeAndSecondsString(from)}")
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }

        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }
}
