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

class LoadStatusWorker(
    context: Context, params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin

    override fun doWork(): Result {
        var ret = Result.success()

        runBlocking {
            try {
                val status = nsClientV3Plugin.nsAndroidClient.getStatus()
                aapsLogger.debug("STATUS: $status")
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