package app.aaps.di.metro

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.aaps.core.objects.workflow.MetroWorkerCreator

/**
 * Builds every worker in the app.
 *
 * WorkManager constructs a worker itself and only hands over a class name, so dependency injection has
 * to hook in through a [WorkerFactory] rather than a constructor call anywhere in our code. Hilt ships
 * one; Metro does not, so this is it.
 *
 * A worker contributes a [MetroWorkerCreator] into a class-keyed multibinding and this looks it up by
 * the class name WorkManager gives us. There used to be a `HiltWorkerFactory` fallback here so the 42
 * `@HiltWorker` classes could move a few at a time; none are left, so the fallback is gone and with it
 * the last thing outside the Dagger-Metro bridge that needed Hilt at runtime.
 *
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
