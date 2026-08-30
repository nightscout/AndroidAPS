package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.RunnerWorker
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.core.objects.workflow.WorkerInstanceFactory
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers

/**
 * The WorkManager side of the phase that follows the prepare pass. The work itself is in
 * [PostCalculationRunner]; [RunnerWorker] does the `Result` mapping.
 */
class PostCalculationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val runner: PostCalculationRunner
) : RunnerWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {

    override suspend fun runBody(isStopped: () -> Boolean): WorkOutcome =
        runner.run(
            job = inputData.getString(WorkflowChainData.JOB_KEY),
            generation = inputData.getLong(WorkflowChainData.GEN_KEY, -1L),
            isStopped = isStopped
        )

    /** Metro builds the worker through this - WorkManager supplies context and params, the graph the rest. */
    @AssistedFactory
    abstract class Factory : WorkerInstanceFactory<PostCalculationWorker>()
}
