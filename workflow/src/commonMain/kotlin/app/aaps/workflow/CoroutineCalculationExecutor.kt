package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.workflow.CalculationWakeLock
import app.aaps.core.objects.workflow.WorkOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the calculation phases as coroutines, one run per job name.
 *
 * This is the executor on every platform, Android included. It is enough because nothing in this
 * chain needs to outlive the process: a run recomputes from the database, so a run lost to a restart
 * is simply started again by whatever asked for it.
 *
 * Replace semantics: starting a job cancels the run already holding that name. The generation check
 * in [WorkflowChainData] still guards the case where a cancelled run gets one more step in before it
 * notices.
 *
 * Two things here copy what WorkManager did for the workers on Android, because the chain was built
 * on them: a phase that throws is a failed phase, logged and not passed on, and the post phase - the
 * one that runs the loop - only runs after a prepare phase that succeeded.
 *
 * One thing is not copied: a phase that reports [WorkOutcome.Retry] is not run again. Neither runner
 * reports it, and the next BG starts a new run anyway.
 *
 * @param wakeLock keeps the device awake for each run, or null when the platform has none
 */
class CoroutineCalculationExecutor(
    private val scope: CoroutineScope,
    private val aapsLogger: AAPSLogger,
    private val prepare: PrepareGraphDataRunner,
    private val post: PostCalculationRunner,
    private val wakeLock: CalculationWakeLock?
) : CalculationExecutor {

    private val mutex = Mutex()

    /** The run holding each job name, and the prepare half of it, which callers can wait for. */
    private val runs = mutableMapOf<String, Job>()
    private val prepareRuns = mutableMapOf<String, Job>()

    /**
     * The "you have been replaced" flag handed to one run.
     *
     * A run cannot answer "am I still the active one?" from [runs] alone. Cancelling is cooperative,
     * so a replaced run keeps going until its next check - and by then its replacement has installed
     * itself under the same name. Asking "is there a live job called MAIN?" therefore answers **yes**
     * to the very run that was just cancelled, and it carries on as though it were current.
     *
     * The consequences were two, and only the first was visible: both runs emitted progress into the
     * same signals, so the bar jumped between their two positions instead of advancing; and the
     * outgoing run went on doing full IOB passes over a dataset that was still growing, which is
     * exactly the wrong moment to be doing work twice - it showed up during the first Nightscout
     * sync, where new data arrives continuously and replacements are frequent.
     *
     * A token per start makes the question answerable: the run holds its own token and only ever
     * reads that one field, so it learns it has been replaced even though its name is still busy.
     *
     * The flag is `@Volatile` and the run reads **the token object**, never [tokens]. Reading the map
     * from the run would be the same bug in a new place: the map is written under [mutex] by
     * `start` / `startPostOnly` / `stop` while the calculation coroutines run on other threads, so
     * there would be no happens-before - a replaced run could keep seeing its own token indefinitely,
     * and an unsynchronised read during a concurrent `put` is not safe on a plain map either.
     */
    private class RunToken {

        @Volatile var stopped = false
    }

    private val tokens = mutableMapOf<String, RunToken>()

    /**
     * Registers the run before it returns, in the order the starts were called.
     *
     * That order matters: the caller writes generation N and then N+1 into [WorkflowChainData], and
     * the run for N+1 must be the one that stays. When the registration ran on a coroutine of its own,
     * two starts close together could reach the mutex the other way round. Run N then replaced run
     * N+1, found its generation stale, and that BG got no loop run. WorkManager kept the call order,
     * so Android never had this before it moved onto this executor.
     */
    override suspend fun start(job: String, generation: Long, runPost: Boolean) {
        mutex.withLock {
            dropRun(job)
            val stopped = claim(job)
            val awake = keepAwake()
            val prepareJob = scope.async {
                runPhase("prepare", job) { prepare.run(job, generation, isStopped = stopped) }
            }
            prepareRuns[job] = prepareJob
            val run = scope.launch {
                val outcome = prepareJob.await()
                if (!runPost) return@launch
                // A failed or stopped prepare phase left no data the loop should act on.
                if (outcome is WorkOutcome.Success || outcome is WorkOutcome.Skipped)
                    runPhase("post", job) { post.run(job, generation, isStopped = stopped) }
                else
                    aapsLogger.debug(LTag.WORKER, "Calculation $job: prepare phase ended with $outcome, post phase not run")
            }
            runs[job] = run
            releaseWhenAllDone(awake, prepareJob, run)
        }
    }

    override suspend fun startPostOnly(job: String, generation: Long) {
        mutex.withLock {
            dropRun(job)
            val stopped = claim(job)
            val awake = keepAwake()
            val run = scope.launch {
                runPhase("post", job) { post.run(job, generation, isStopped = stopped) }
            }
            runs[job] = run
            releaseWhenAllDone(awake, run)
        }
    }

    override suspend fun stop(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation: $from")
        // Both halves, not just the outer job. The prepare phase is a sibling on [scope], not a child
        // of the job in [runs], so cancelling that one leaves prepare running - and the join below
        // would then report "stopped" while a full IOB pass was still in flight. The one caller that
        // matters is `IobCobCalculatorPlugin.newHistoryData`, which uses this as the barrier before it
        // invalidates the IOB and basal tables.
        val running = mutex.withLock {
            // The token goes first: a run that is mid-phase must see itself as stopped even while
            // its job is still winding down.
            tokens.remove(job)?.stopped = true
            listOfNotNull(prepareRuns.remove(job), runs.remove(job))
        }
        if (running.isEmpty()) return
        running.forEach { it.cancel() }
        val finished = withTimeoutOrNull(STOP_WAIT) { running.joinAll() }
        if (finished == null) aapsLogger.warn(LTag.WORKER, "Calculation did not stop within $STOP_WAIT: $from")
        else aapsLogger.debug(LTag.WORKER, "Calculation stopped: $from")
    }

    override suspend fun waitForPrepare(job: String, reason: String) {
        var logged = false
        // Asked again after every join, like awaitIdle: a prepare phase that was replaced also ends,
        // and the caller wants the data of the one that replaced it.
        val finished = withTimeoutOrNull(STOP_WAIT) {
            while (true) {
                val prepareJob = mutex.withLock { prepareRuns[job] }
                if (prepareJob == null || prepareJob.isCompleted) break
                if (!logged) {
                    aapsLogger.debug(LTag.AUTOSENS, "Waiting for calculation to finish: $reason")
                    logged = true
                }
                prepareJob.join()
            }
        }
        if (finished == null) aapsLogger.warn(LTag.AUTOSENS, "Calculation did not finish within $STOP_WAIT: $reason")
    }

    override suspend fun awaitIdle(job: String, timeout: Duration): Boolean {
        var logged = false
        // The outer job ends after the post phase, so joining it waits for both phases. Asked again
        // after every join: a run that was replaced also ends, and its replacement is then the one
        // still running under this name.
        return withTimeoutOrNull(timeout) {
            while (true) {
                val run = mutex.withLock { runs[job] }
                if (run == null || run.isCompleted) break
                if (!logged) {
                    aapsLogger.debug(LTag.WORKER, "Waiting for $job to finish")
                    logged = true
                }
                run.join()
            }
        } != null
    }

    /**
     * Runs one phase and turns a throw into a [WorkOutcome.Failure].
     *
     * WorkManager did this for the workers: a worker that threw was a failed worker, and the chain
     * behind it did not run. Without it the throw would reach [scope], which has no handler on
     * Android, so one bad calculation would end the whole app, and the pump connection with it.
     * Throwable rather than Exception for the same reason: the worker caught everything.
     */
    private suspend fun runPhase(phase: String, job: String, body: suspend () -> WorkOutcome): WorkOutcome =
        try {
            body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            aapsLogger.error(LTag.WORKER, "Calculation $job: $phase phase failed", e)
            WorkOutcome.Failure("$phase phase threw $e")
        }

    override fun keepAwake(timeout: Duration): CalculationWakeLock.Held? = wakeLock?.acquire(timeout.inWholeMilliseconds)

    private fun keepAwake(): CalculationWakeLock.Held? = keepAwake(WAKE_LOCK_TIMEOUT)

    /**
     * Releases [awake] when every one of [jobs] has ended, however they end.
     *
     * All of them, not only the outer job: when a run is stopped, the outer job ends at once, while the
     * prepare half is still running up to its next check, and [stop] waits for that. Completion
     * handlers rather than a `finally`: a run that is replaced before it was ever scheduled never
     * enters its body, but it still completes.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun releaseWhenAllDone(awake: CalculationWakeLock.Held?, vararg jobs: Job) {
        if (awake == null) return
        val running = AtomicInt(jobs.size)
        jobs.forEach { job ->
            job.invokeOnCompletion { if (running.addAndFetch(-1) == 0) awake.release() }
        }
    }

    /**
     * Takes ownership of [job] for the run being started, and returns the check that run should use.
     *
     * Called while the mutex is held, so two starts cannot take the same token.
     */
    private fun claim(job: String): () -> Boolean {
        val token = RunToken().also { tokens[job] = it }
        return { token.stopped }
    }

    /**
     * Lets go of [job] and cancels both halves of the run holding it. Call with the mutex held.
     *
     * The token is flagged as well as the jobs cancelled, because cancelling is cooperative: the
     * outgoing run keeps going until its next check, and the flag is what that check reads.
     */
    private fun dropRun(job: String) {
        tokens.remove(job)?.stopped = true
        prepareRuns.remove(job)?.cancel()
        runs.remove(job)?.cancel()
    }

    companion object {

        private val STOP_WAIT = 5.seconds

        /**
         * Only a safety limit: a run gives its lock back as soon as it ends, normally within seconds.
         * Long enough for a full recalculation on a slow phone followed by the loop, and the same
         * time `HistoryChangeScheduler` lets a calculation run before it stops it.
         */
        private val WAKE_LOCK_TIMEOUT = 10.minutes
    }
}
