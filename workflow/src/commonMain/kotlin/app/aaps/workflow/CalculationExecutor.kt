package app.aaps.workflow

import kotlin.time.Duration

/**
 * Runs the two calculation phases, under a name that only one run may hold at a time.
 *
 * This is the only part of the calculation chain that differs per platform. Android runs the phases
 * as WorkManager workers because they can outlive the screen that asked for them; everywhere else a
 * coroutine is enough, because nothing there needs to survive process death - the chain recomputes
 * from the database whenever it next runs.
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
     */
    fun start(job: String, generation: Long, runPost: Boolean)

    /** Starts only the post phase under [job], replacing any run already going under it. */
    fun startPostOnly(job: String, generation: Long)

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
}
