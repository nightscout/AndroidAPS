package app.aaps.di.metro

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.aaps.core.objects.workflow.MetroWorkerCreator

/**
 * Builds every worker in the app.
 * Returning null is WorkManager's documented "I do not handle this one" signal, and it still matters:
 * WorkManager's own internal workers are not ours and are built reflectively, exactly as before.
 */
class MetroWorkerFactory(
    private val metroGraphs: MetroGraphs
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? =
        metroGraphs.workerCreators()[workerClassName]?.create(appContext, workerParameters)
}
