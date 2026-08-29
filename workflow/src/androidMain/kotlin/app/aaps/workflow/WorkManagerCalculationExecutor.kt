package app.aaps.workflow

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.worker.then
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Runs the calculation phases as WorkManager workers.
 *
 * Android keeps WorkManager here because a calculation started from the overview should finish even
 * if the screen goes away. The phases themselves are in [PrepareGraphDataRunner] and
 * [PostCalculationRunner]; the workers are shims.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class WorkManagerCalculationExecutor @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : CalculationExecutor {

    private val workManager get() = WorkManager.getInstance(context)

    override fun start(job: String, generation: Long, runPost: Boolean) {
        val jobData = dataForJob(job, generation)
        workManager
            .beginUniqueWork(
                job, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(PrepareGraphDataWorker::class.java)
                    .setInputData(jobData)
                    // Tag only the data-producing stage so waitForPrepare() can await it without
                    // waiting on the loop-invoking post stage (would self-stall).
                    .addTag(prepareTag(job))
                    .build()
            )
            .then(
                runIf = runPost,
                OneTimeWorkRequest.Builder(PostCalculationWorker::class.java)
                    .setInputData(jobData)
                    .build()
            )
            .enqueue()
    }

    override fun startPostOnly(job: String, generation: Long) {
        workManager.enqueueUniqueWork(
            job, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(PostCalculationWorker::class.java)
                .setInputData(dataForJob(job, generation))
                .build()
        )
    }

    override suspend fun stop(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation thread: $from")
        workManager.cancelUniqueWork(job)
        val deadline = Clock.System.now().toEpochMilliseconds() + STOP_WAIT_TIMEOUT_MS
        while (Clock.System.now().toEpochMilliseconds() < deadline) {
            val workStatus = workManager.getWorkInfosForUniqueWork(job).get()
            if (workStatus.isEmpty() || workStatus[0].state != WorkInfo.State.RUNNING) {
                aapsLogger.debug(LTag.WORKER, "Calculation thread stopped: $from")
                return
            }
            // delay, not SystemClock.sleep: this no longer blocks the caller's thread.
            delay(STOP_WAIT_POLL_MS)
        }
        aapsLogger.warn(LTag.WORKER, "Calculation thread did not stop within ${STOP_WAIT_TIMEOUT_MS}ms: $from")
    }

    override suspend fun waitForPrepare(job: String, reason: String) {
        val tag = prepareTag(job)
        val deadline = Clock.System.now().toEpochMilliseconds() + STOP_WAIT_TIMEOUT_MS
        var logged = false
        while (Clock.System.now().toEpochMilliseconds() < deadline) {
            // Old finished works keep their tag until WorkManager prunes them; only unfinished ones matter.
            val running = workManager.getWorkInfosByTag(tag).get().any { !it.state.isFinished }
            if (!running) return
            if (!logged) {
                aapsLogger.debug(LTag.AUTOSENS, "Waiting for calculation to finish: $reason")
                logged = true
            }
            delay(STOP_WAIT_POLL_MS)
        }
        aapsLogger.warn(LTag.AUTOSENS, "Calculation did not finish within ${STOP_WAIT_TIMEOUT_MS}ms: $reason")
    }

    // Distinct per-job tag on the prepare (data-producing) stage; used by waitForPrepare().
    private fun prepareTag(job: String): String = "$job:prepare"

    private fun dataForJob(job: String, generation: Long): Data =
        Data.Builder()
            .putString(WorkflowChainData.JOB_KEY, job)
            .putLong(WorkflowChainData.GEN_KEY, generation)
            .build()

    companion object {

        private const val STOP_WAIT_TIMEOUT_MS = 5_000L
        private const val STOP_WAIT_POLL_MS = 100L
    }
}
