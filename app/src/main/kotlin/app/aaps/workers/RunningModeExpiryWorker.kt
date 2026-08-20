package app.aaps.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers

/**
 * WorkManager trigger for [RunningModeExpiryJob]. The job holds what happens and why; this holds only
 * when.
 *
 * It lives in :app rather than in :plugins:aps because `@HiltWorker` and `@AssistedInject` are
 * answered with generated Java, which a multiplatform module cannot compile - see
 * `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken". Note that the abstract [LoggingWorker] it
 * extends DOES live in a multiplatform module: it carries no annotations, so nothing has to be
 * generated for it. The line is the annotation, not WorkManager.
 *
 * There are ~42 of these across the repo. If they are ever unified behind one generic worker
 * dispatching on a job id, this is the shape to unify: a thin envelope over a platform free job.
 */
@HiltWorker
class RunningModeExpiryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val job: RunningModeExpiryJob
) : LoggingWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        job.run()
        return Result.success()
    }

    companion object {

        const val WORK_NAME = "RunningModeExpiry"
    }
}
