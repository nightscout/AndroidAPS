package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.logging.AAPSLogger
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class LoadLastModificationWorker(
    context: Context, params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin

    override fun doWork(): Result {
        var ret = Result.success()

        runBlocking {
            try {
                val lm = nsClientV3Plugin.nsAndroidClient.getLastModified()
                nsClientV3Plugin.lastModified = lm
                aapsLogger.debug("LAST MODIFIED: ${nsClientV3Plugin.lastModified}")
            } catch (error: Exception) {
                aapsLogger.error("Error: ", error)
                ret = Result.failure(workDataOf("Error" to error.toString()))
            }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}