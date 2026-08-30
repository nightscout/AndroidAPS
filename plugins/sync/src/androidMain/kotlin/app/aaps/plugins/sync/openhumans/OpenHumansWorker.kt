package app.aaps.plugins.sync.openhumans

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers

class OpenHumansWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val openHumansUploader: OpenHumansUploaderPlugin
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

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

    /** Metro builds the worker through this, replacing what `@HiltWorker` did. */
    @AssistedFactory
    fun interface Factory : MetroWorkerCreator {

        override fun create(context: Context, params: WorkerParameters): OpenHumansWorker
    }
}
