package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.interfaces.NSAndroidClient

import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import dev.zacsweers.metro.Inject
import kotlin.math.max

@Inject
class LoadDeviceStatusRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val nsClientRepository: NSClientRepository,
    private val config: Config
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")

        try {
            // Notify plugin we loaded al missed data
            nsClientV3Plugin.initialLoadFinished = true

            if (config.AAPSCLIENT) loadAllSinceLastLoad(nsAndroidClient)
            else loadNewest(nsAndroidClient)
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }

        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }

    /** A master needs only the newest device status. */
    private suspend fun loadNewest(nsAndroidClient: NSAndroidClient) {
        val from = dateUtil.now() - T.mins(7).msecs()
        val deviceStatuses = nsAndroidClient.getDeviceStatusModifiedSince(from, NSClientV3Plugin.RECORDS_TO_LOAD)
        aapsLogger.debug("DEVICESTATUSES: $deviceStatuses")
        if (deviceStatuses.isNotEmpty()) {
            nsClientRepository.addLog("◄ RCV", "${deviceStatuses.size} DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}")
            nsDeviceStatusHandler.handleNewData(deviceStatuses.toTypedArray())
            nsClientRepository.addLog("● DONE PROCESSING DS", "")
        } else {
            nsClientRepository.addLog("◄ RCV DS END", "No data from ${dateUtil.dateAndTimeAndSecondsString(from)}")
        }
    }

    /**
     * A client works out the ISF for carbs from the master's loop results of the last 48 hours, and those
     * come with the device statuses (`IsfForCarbs`). So it loads all of them, not only the newest: 48 hours
     * on the first load after a start, then everything the server got since the last load - also what came
     * while this phone was offline. A day holds about 300, so they come in pages.
     */
    private suspend fun loadAllSinceLastLoad(nsAndroidClient: NSAndroidClient) {
        var from = max(nsClientV3Plugin.lastLoadedDeviceStatusSrvModified, dateUtil.now() - T.hours(48).msecs())
        while (true) {
            val deviceStatuses = nsAndroidClient.getDeviceStatusModifiedSince(from, NSClientV3Plugin.RECORDS_TO_LOAD)
            if (deviceStatuses.isEmpty()) {
                nsClientRepository.addLog("◄ RCV DS END", "No data from ${dateUtil.dateAndTimeAndSecondsString(from)}")
                return
            }
            nsClientRepository.addLog("◄ RCV", "${deviceStatuses.size} DSs from ${dateUtil.dateAndTimeAndSecondsString(from)}")
            nsDeviceStatusHandler.handleNewData(deviceStatuses.toTypedArray())
            nsClientRepository.addLog("● DONE PROCESSING DS", "")
            // The next page starts after the newest change of this one. Without a newer one the server cannot
            // be asked for more, so the load ends, and the next load starts from the same point.
            val newest = deviceStatuses.mapNotNull { it.srvModified }.maxOrNull() ?: return
            if (newest <= from) return
            from = newest
            nsClientV3Plugin.lastLoadedDeviceStatusSrvModified = newest
            if (deviceStatuses.size < NSClientV3Plugin.RECORDS_TO_LOAD) return
        }
    }
}
