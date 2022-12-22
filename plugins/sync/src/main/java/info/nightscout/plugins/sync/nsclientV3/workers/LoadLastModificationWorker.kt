package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadLastModificationWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params) {

    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin

    override fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))
        var ret = Result.success()

        runBlocking {
            try {
                val lm = nsAndroidClient.getLastModified()
                nsClientV3Plugin.newestDataOnServer = lm
                aapsLogger.debug("LAST MODIFIED: ${nsClientV3Plugin.newestDataOnServer}")
            } catch (error: Exception) {
                aapsLogger.error("Error: ", error)
                ret = Result.failure(workDataOf("Error" to error.toString()))
            }
        }
        return ret
    }
}