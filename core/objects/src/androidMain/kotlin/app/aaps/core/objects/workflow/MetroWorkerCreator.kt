package app.aaps.core.objects.workflow

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/**
 * What a worker wired by Metro contributes so the app's `WorkerFactory` can build it.
 *
 * `@HiltWorker` is the part of Hilt that has to be replaced before Dagger can be removed - 42 of them
 * in this tree. WorkManager constructs a worker itself and only hands over a class name, so dependency
 * injection has to hook in through a `WorkerFactory` rather than a constructor call in our code. Hilt
 * ships one; Metro does not, so the app supplies `MetroWorkerFactory` and every converted worker
 * contributes one of these into a class-keyed multibinding.
 *
 * The two arguments are the ones only WorkManager has, which makes every worker an assisted-injection
 * case: graph dependencies plus a caller-supplied [Context] and [WorkerParameters]. Metro generates the
 * implementation from `@AssistedFactory`, so a worker only declares a nested factory extending this.
 *
 * It lives beside [LoggingWorker] rather than in `:app` because workers live in many modules and none
 * of them can depend on `:app`.
 */
fun interface MetroWorkerCreator {

    fun create(context: Context, params: WorkerParameters): ListenableWorker
}
