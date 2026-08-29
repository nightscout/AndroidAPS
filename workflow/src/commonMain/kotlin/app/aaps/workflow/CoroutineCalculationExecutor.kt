package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the calculation phases as coroutines, one run per job name.
 *
 * This is the executor for every platform that has no WorkManager. It is enough because nothing in
 * this chain needs to outlive the process: a run recomputes from the database, so a run lost to a
 * restart is simply started again by whatever asked for it.
 *
 * Replace semantics are the same as the Android one: starting a job cancels the run already holding
 * that name. The generation check in [WorkflowChainData] still guards the case where a cancelled run
 * gets one more step in before it notices.
 */
class CoroutineCalculationExecutor(
    private val scope: CoroutineScope,
    private val aapsLogger: AAPSLogger,
    private val prepare: PrepareGraphDataRunner,
    private val post: PostCalculationRunner
) : CalculationExecutor {

    private val mutex = Mutex()

    /** The run holding each job name, and the prepare half of it, which callers can wait for. */
    private val runs = mutableMapOf<String, Job>()
    private val prepareRuns = mutableMapOf<String, Job>()

    override fun start(job: String, generation: Long, runPost: Boolean) {
        scope.launch {
            mutex.withLock {
                runs.remove(job)?.cancel()
                val prepareJob = scope.launch {
                    prepare.run(job, generation, isStopped = { !isActiveJob(job) })
                }
                prepareRuns[job] = prepareJob
                runs[job] = scope.launch {
                    prepareJob.join()
                    if (runPost) post.run(job, generation, isStopped = { !isActiveJob(job) })
                }
            }
        }
    }

    override fun startPostOnly(job: String, generation: Long) {
        scope.launch {
            mutex.withLock {
                runs.remove(job)?.cancel()
                runs[job] = scope.launch { post.run(job, generation, isStopped = { !isActiveJob(job) }) }
            }
        }
    }

    override suspend fun stop(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation: $from")
        val running = mutex.withLock { runs.remove(job).also { prepareRuns.remove(job) } } ?: return
        running.cancel()
        val finished = withTimeoutOrNull(STOP_WAIT) { running.join() }
        if (finished == null) aapsLogger.warn(LTag.WORKER, "Calculation did not stop within $STOP_WAIT: $from")
        else aapsLogger.debug(LTag.WORKER, "Calculation stopped: $from")
    }

    override suspend fun waitForPrepare(job: String, reason: String) {
        val prepareJob = mutex.withLock { prepareRuns[job] } ?: return
        if (prepareJob.isCompleted) return
        aapsLogger.debug(LTag.AUTOSENS, "Waiting for calculation to finish: $reason")
        val finished = withTimeoutOrNull(STOP_WAIT) { prepareJob.join() }
        if (finished == null) aapsLogger.warn(LTag.AUTOSENS, "Calculation did not finish within $STOP_WAIT: $reason")
    }

    /** A phase asks this between steps: false once the run no longer holds its job name. */
    private fun isActiveJob(job: String): Boolean = runs[job]?.isCancelled == false

    companion object {

        private val STOP_WAIT = 5.seconds
    }
}
