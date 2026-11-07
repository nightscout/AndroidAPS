package app.aaps.settingsstore

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Builds the app's jobs while the settings cannot be opened: each one does nothing and reports it is done.
 *
 * So nothing runs on settings that are not the user's. A periodic job, such as the keep-alive, simply comes
 * again at its next normal time. A one-time job is dropped; a BG reading that arrived meanwhile is lost
 * anyway, because the inbox that holds it lives in memory. "Retry" instead of "done" would be worse: its
 * wait doubles each time, up to hours, and the keep-alive would still be waiting after a good start.
 *
 * WorkManager's own jobs are left to WorkManager, the same as `MetroWorkerFactory` does.
 *
 * @param appJobs the class names of the app's jobs.
 */
class IdleWorkerFactory(private val appJobs: () -> Set<String>) : WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName in appJobs()) IdleWorker(appContext, workerParameters) else null

    private class IdleWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {

        override fun doWork(): Result = Result.success()
    }
}
