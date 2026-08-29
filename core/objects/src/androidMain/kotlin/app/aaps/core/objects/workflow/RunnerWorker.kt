package app.aaps.core.objects.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A [LoggingWorker] whose body lives in commonMain.
 *
 * The point of the split is that a worker body is almost always plain Kotlin - it reads the
 * database, computes, posts events - wrapped in a thin layer that only exists because WorkManager
 * needs a class it can construct. Subclasses implement [runBody] and get the `Result` mapping for
 * free, so the mapping is written once rather than in every worker.
 *
 * A subclass should hold its runner and do nothing else:
 *
 * ```
 * class ThingWorker @AssistedInject constructor(
 *     @Assisted context: Context, @Assisted params: WorkerParameters,
 *     aapsLogger: AAPSLogger, fabricPrivacy: FabricPrivacy,
 *     private val runner: ThingRunner
 * ) : RunnerWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {
 *
 *     override suspend fun runBody(isStopped: () -> Boolean) = runner.run(isStopped)
 *
 *     @AssistedFactory abstract class Factory : WorkerInstanceFactory<ThingWorker>()
 * }
 * ```
 *
 * [runBody] is handed WorkManager's stop flag as a plain lambda. A long body should ask it between
 * phases so a replaced run gives up promptly, without the body knowing what stopped it.
 */
abstract class RunnerWorker(
    context: Context,
    params: WorkerParameters,
    dispatcher: CoroutineDispatcher,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy
) : LoggingWorker(context, params, dispatcher, aapsLogger, fabricPrivacy) {

    abstract suspend fun runBody(isStopped: () -> Boolean): WorkOutcome

    final override suspend fun doWorkAndLog(): Result =
        when (val outcome = runBody { isStopped }) {
            is WorkOutcome.Success -> Result.success()
            is WorkOutcome.Failure -> Result.failure(workDataOf("Error" to outcome.reason))
        }
}
