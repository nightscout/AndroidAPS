package app.aaps.workflow

import app.aaps.core.interfaces.workflow.CalculationWakeLock
import kotlin.time.Duration

/**
 * Runs the two calculation phases, under a name that only one run may hold at a time.
 *
 * Every platform runs the phases as coroutines, in [CoroutineCalculationExecutor]. Nothing here needs
 * to survive process death - the chain recomputes from the database whenever it next runs. What
 * differs per platform is only how the device is kept awake during a run, see [CalculationWakeLock].
 *
 * Not WorkManager, although Android used it here before. Android counts the run time of every job
 * against a quota, and since Android 16 also for an app with a foreground service: 4 hours in 24
 * hours, even in the exempted standby bucket. Once that was used up, the system stopped the
 * calculation a few seconds into every attempt, and the loop, which runs at the end of this chain,
 * did not run for long periods.
 *
 * It is deliberately not a general background-work API. It says what this chain needs: start under a
 * name replacing whatever was running, stop and wait, and wait for the data-producing half. Anything
 * needing constraints, retries or execution after the process dies does not belong here.
 */
interface CalculationExecutor {

    /**
     * Starts the prepare phase, followed by the post phase when [runPost], under [job].
     *
     * Replaces any run already going under [job]. [generation] identifies which chain the phases
     * belong to; they hand it back to [WorkflowChainData] to pick up their input, and a phase from a
     * replaced run gets nothing.
     *
     * The run is registered before this returns, so starts replace each other in the order they were
     * called, and a [stop] that follows sees this run.
     */
    suspend fun start(job: String, generation: Long, runPost: Boolean)

    /** Starts only the post phase under [job], replacing any run already going under it. Registered like [start]. */
    suspend fun startPostOnly(job: String, generation: Long)

    /** Cancels [job] and waits until it has actually stopped, or until the wait times out. */
    suspend fun stop(job: String, from: String)

    /**
     * Waits until the prepare phase of [job] is no longer running.
     *
     * Only the prepare phase, because the post phase invokes the loop; waiting on that from inside
     * the loop would stall it.
     */
    suspend fun waitForPrepare(job: String, reason: String)

    /**
     * Waits until no phase of [job] is running or waiting to run, or until [timeout].
     *
     * Both phases, unlike [waitForPrepare]: the caller wants to start the next run, and starting it
     * while the post phase runs would cancel the loop in the middle of a run.
     *
     * @return true when [job] is idle, false when [timeout] ran out first
     */
    suspend fun awaitIdle(job: String, timeout: Duration): Boolean

    /**
     * Keeps the device awake with the same lock a run takes, until the handle is released or [timeout]
     * ran out. Null when the platform has no wake lock. See
     * [app.aaps.core.interfaces.workflow.CalculationWorkflow.keepAwake] for what it is for.
     */
    fun keepAwake(timeout: Duration): CalculationWakeLock.Held?
}
