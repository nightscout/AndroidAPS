package app.aaps.di.metro

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.aaps.core.objects.workflow.MetroWorkerCreator

/**
 * Builds workers whose wiring has moved to Metro, and hands everything else back to Hilt.
 *
 * `@HiltWorker` is the piece of Hilt that has to be replaced before Dagger can be removed - 42 of them
 * in this tree. WorkManager constructs a worker itself, so dependency injection has to hook in through
 * a [WorkerFactory] rather than through a constructor call anywhere in our code. Hilt supplies one;
 * Metro does not, so this is it.
 *
 * A worker converted to Metro contributes a [MetroWorkerCreator] into a class-keyed multibinding, and
 * this looks it up by the class name WorkManager gives us. Anything not in the map is not converted
 * yet, so it goes to [hiltWorkerFactory] exactly as before. That is what lets the 42 move a few at a
 * time instead of in one commit - and when the map holds all of them, the Hilt fallback is deleted
 * along with Hilt itself.
 *
 * Returning null from a factory is WorkManager's documented "I do not handle this one" signal, so the
 * fallback chain is the framework's own mechanism rather than something invented here.
 */
class MetroWorkerFactory(
    private val metroGraphs: MetroGraphs,
    private val hiltWorkerFactory: HiltWorkerFactory
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? =
        metroGraphs.workerCreators()[workerClassName]?.create(appContext, workerParameters)
            ?: hiltWorkerFactory.createWorker(appContext, workerClassName, workerParameters)
}
