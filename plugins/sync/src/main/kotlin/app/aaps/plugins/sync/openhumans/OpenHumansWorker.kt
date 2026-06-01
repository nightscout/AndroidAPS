package app.aaps.plugins.sync.openhumans

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers

@HiltWorker
class OpenHumansWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val openHumansUploader: OpenHumansUploaderPlugin
) : LoggingWorker(context, workerParameters, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        return try {
            aapsLogger.info(LTag.OHUPLOADER, "Starting upload")
            openHumansUploader.uploadData()
            aapsLogger.info(LTag.OHUPLOADER, "Upload finished")
            Result.success()
        } catch (e: Exception) {
            aapsLogger.error(LTag.OHUPLOADER, "OH Uploader failed", e)
            Result.failure()
        }
    }
}
