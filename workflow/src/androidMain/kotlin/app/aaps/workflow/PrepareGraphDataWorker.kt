package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.WorkerInstanceFactory
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers

/**
 * The WorkManager side of the prepare phase.
 *
 * All it does is read where the chain state lives, hand that to [PrepareGraphDataRunner] and turn the
 * answer into a `Result`. The calculation itself is in commonMain, so it is not tied to WorkManager
 * or to Android - see [PrepareGraphDataRunner].
 */
class PrepareGraphDataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val runner: PrepareGraphDataRunner
) : LoggingWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        val outcome = runner.run(
            job = inputData.getString(WorkflowChainData.JOB_KEY),
            generation = inputData.getLong(WorkflowChainData.GEN_KEY, -1L),
            // WorkManager's own flag: the runner asks it between phases so a replaced chain stops.
            isStopped = { isStopped }
        )
        return when (outcome) {
            is CalculationOutcome.Success -> Result.success()
            is CalculationOutcome.Failure -> Result.failure(workDataOf("Error" to outcome.reason))
        }
    }

    /** Metro builds the worker through this - WorkManager supplies context and params, the graph the rest. */
    @AssistedFactory
    abstract class Factory : WorkerInstanceFactory<PrepareGraphDataWorker>()
}
