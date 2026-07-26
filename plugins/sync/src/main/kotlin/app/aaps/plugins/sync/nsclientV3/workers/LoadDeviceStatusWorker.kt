package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers

@HiltWorker
class LoadDeviceStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val nsClientRepository: NSClientRepository
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

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
            nsClientRepository.addLog("◄ ERROR", error.localizedMessage)
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        nsClientV3Plugin.lastOperationError = null
        return Result.success()
    }
}
