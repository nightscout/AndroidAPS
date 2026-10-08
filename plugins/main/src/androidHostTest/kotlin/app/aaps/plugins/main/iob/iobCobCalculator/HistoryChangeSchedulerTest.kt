package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How history changes become recalculations, in virtual time.
 *
 * The case this was written for: with a 1-minute CGM a full recalculation took longer than a minute,
 * every new glucose value stopped it, the next run started again from the oldest data, and no run ever
 * finished - so the loop, which runs after a finished calculation, stopped for an hour. A change that
 * arrives while a calculation runs must now wait for it instead, and must not get lost doing so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryChangeSchedulerTest {

    private data class Run(val oldDataTimestamp: Long, val reloadBgData: Boolean, val triggeredByNewBG: Boolean)

    private val runs = mutableListOf<Run>()
    private var failures = 0

    /** The calculation that is running, or null when nothing runs. */
    private var runningCalculation: CompletableDeferred<Unit>? = null

    /** What [HistoryChangeScheduler] launches into. Null means a stopped plugin. */
    private var scope: CoroutineScope? = null

    /** The run fails while this is above zero, once per run. */
    private var failingRuns = 0

    /** When set, the next run waits for it before it returns. The scheduler lock is held meanwhile. */
    private var runGate: CompletableDeferred<Unit>? = null

    /**
     * Called once, at the very end of the next wait for the calculation. The waiting body comes out of
     * the wait right after it and then takes the lock - so this is the moment where another change or
     * a drop can get in first.
     */
    private var atEndOfWait: (suspend () -> Unit)? = null

    private fun TestScope.scheduler(maxWait: Duration = HistoryChangeScheduler.MAX_WAIT_FOR_CALCULATION): HistoryChangeScheduler {
        scope = backgroundScope
        return HistoryChangeScheduler(
            aapsLogger = AAPSLoggerTest(),
            scope = { scope },
            awaitCalculationIdle = { timeout ->
                val running = runningCalculation
                val idle = running == null || withTimeoutOrNull(timeout) { running.await() } != null
                atEndOfWait?.also { atEndOfWait = null }?.invoke()
                idle
            },
            onRunFailed = { failures++ },
            maxWaitForCalculation = maxWait
        ) { oldDataTimestamp, reloadBgData, triggeredByNewBG ->
            if (failingRuns > 0) {
                failingRuns--
                error("invalidation failed")
            }
            runs += Run(oldDataTimestamp, reloadBgData, triggeredByNewBG)
            runGate?.also { runGate = null }?.await()
        }
    }

    /**
     * Lets the debounce run out and whatever follows run. Not `advanceUntilIdle`: that stops as soon
     * as only `backgroundScope` work is left, which is all the scheduler launches.
     */
    private fun TestScope.settle() {
        advanceTimeBy(HistoryChangeScheduler.DEBOUNCE + 1.seconds)
        runCurrent()
    }

    @Test
    fun `with no calculation running the change runs after the debounce`() = runTest {
        val sut = scheduler()

        sut.schedule(1_000, reloadBgData = true, triggeredByNewBG = true)
        advanceTimeBy(HistoryChangeScheduler.DEBOUNCE - 100.milliseconds)
        assertThat(runs).isEmpty()

        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertThat(runs).containsExactly(Run(1_000, reloadBgData = true, triggeredByNewBG = true))
    }

    /** One recalculation for a burst, from the oldest change, with the flags of all of them. */
    @Test
    fun `a burst of changes runs once with the oldest timestamp and the merged flags`() = runTest {
        val sut = scheduler()

        sut.schedule(3_000, reloadBgData = false, triggeredByNewBG = true)
        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = false)
        sut.schedule(2_000, reloadBgData = true, triggeredByNewBG = false)
        settle()

        assertThat(runs).containsExactly(Run(1_000, reloadBgData = true, triggeredByNewBG = true))
    }

    /**
     * The 1-minute CGM case. Three new values arrive while a long calculation runs. None of them may
     * stop it, all of them must be calculated afterwards - in one run, from the oldest.
     */
    @Test
    fun `changes wait for the running calculation and run once when it ends`() = runTest {
        val sut = scheduler()
        val calculation = CompletableDeferred<Unit>().also { runningCalculation = it }

        // Different flags on purpose, so the merge during the wait is checked too
        sut.schedule(60_000, reloadBgData = false, triggeredByNewBG = false)
        advanceTimeBy(1.minutes)
        sut.schedule(120_000, reloadBgData = true, triggeredByNewBG = false)
        advanceTimeBy(1.minutes)
        sut.schedule(180_000, reloadBgData = false, triggeredByNewBG = true)
        advanceTimeBy(1.minutes)
        assertThat(runs).isEmpty() // still running, so nothing was stopped and nothing ran

        calculation.complete(Unit)
        runningCalculation = null
        runCurrent()

        assertThat(runs).containsExactly(Run(60_000, reloadBgData = true, triggeredByNewBG = true))
    }

    /** An older change arriving during the wait takes over the waiting one, flags included. */
    @Test
    fun `an older change during the wait replaces the waiting one`() = runTest {
        val sut = scheduler()
        val calculation = CompletableDeferred<Unit>().also { runningCalculation = it }

        sut.schedule(120_000, reloadBgData = true, triggeredByNewBG = true)
        advanceTimeBy(1.minutes)
        sut.schedule(30_000, reloadBgData = false, triggeredByNewBG = false) // a treatment from earlier
        advanceTimeBy(1.minutes)
        calculation.complete(Unit)
        runningCalculation = null
        settle()

        assertThat(runs).containsExactly(Run(30_000, reloadBgData = true, triggeredByNewBG = true))
    }

    /** A calculation that hangs must not hold changes back for ever: after the limit the change runs. */
    @Test
    fun `waiting gives up after the limit and runs the change anyway`() = runTest {
        val sut = scheduler(maxWait = 10.minutes)
        runningCalculation = CompletableDeferred() // never ends

        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = true)
        advanceTimeBy(HistoryChangeScheduler.DEBOUNCE + 10.minutes - 1.seconds)
        assertThat(runs).isEmpty()

        advanceTimeBy(2.seconds)
        runCurrent()
        assertThat(runs).hasSize(1)
    }

    /**
     * A full recalculation drops the waiting change. It must not run afterwards, and the scheduler
     * must still take new changes: an entry left behind would swallow every later one (issue #5066).
     */
    @Test
    fun `a dropped change does not run and later changes still do`() = runTest {
        val sut = scheduler()
        val calculation = CompletableDeferred<Unit>().also { runningCalculation = it }
        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = true)
        advanceTimeBy(1.minutes)

        sut.drop()
        calculation.complete(Unit)
        runningCalculation = null
        settle()
        assertThat(runs).isEmpty()

        sut.schedule(2_000, reloadBgData = false, triggeredByNewBG = true)
        settle()
        assertThat(runs).containsExactly(Run(2_000, reloadBgData = false, triggeredByNewBG = true))
    }

    /**
     * A drop that gets in between the end of the wait and the lock. The waiting body cannot be
     * cancelled any more at that point, so it must see for itself that its change is gone - otherwise
     * it recalculates on top of the full recalculation that dropped it.
     */
    @Test
    fun `a change dropped right at the end of its wait does not run`() = runTest {
        val sut = scheduler()
        atEndOfWait = { sut.drop() }

        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = true)
        settle()

        assertThat(runs).isEmpty()
    }

    /**
     * An older change that gets in at the same moment replaces the waiting one. Only the replacement
     * may run - it carries the flags of both - and not the replaced one as well.
     */
    @Test
    fun `a change replaced right at the end of its wait runs only once`() = runTest {
        val sut = scheduler()
        atEndOfWait = { sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = false) }

        sut.schedule(5_000, reloadBgData = true, triggeredByNewBG = true)
        settle()
        settle()

        assertThat(runs).containsExactly(Run(1_000, reloadBgData = true, triggeredByNewBG = true))
    }

    /** A change that arrives while a run holds the lock waits for it and then runs on its own. */
    @Test
    fun `a change during a run is scheduled after it`() = runTest {
        val sut = scheduler()
        val gate = CompletableDeferred<Unit>().also { runGate = it }
        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = true)
        settle() // the run started and now holds the lock

        val second = backgroundScope.launch { sut.schedule(2_000, reloadBgData = true, triggeredByNewBG = false) }
        runCurrent()
        assertThat(second.isCompleted).isFalse() // waiting for the lock

        gate.complete(Unit)
        settle()

        assertThat(runs).containsExactly(
            Run(1_000, reloadBgData = false, triggeredByNewBG = true),
            Run(2_000, reloadBgData = true, triggeredByNewBG = false)
        ).inOrder()
    }

    /** A failing run drops the cached data and leaves the scheduler working. */
    @Test
    fun `a failing run calls onRunFailed and later changes still run`() = runTest {
        val sut = scheduler()
        failingRuns = 1

        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = false)
        settle()
        assertThat(failures).isEqualTo(1)
        assertThat(runs).isEmpty()

        sut.schedule(2_000, reloadBgData = false, triggeredByNewBG = false)
        settle()
        assertThat(runs).containsExactly(Run(2_000, reloadBgData = false, triggeredByNewBG = false))
    }

    /** A change for a stopped plugin is dropped and does not block the next one after a restart. */
    @Test
    fun `a change for a stopped plugin is dropped and later changes still run`() = runTest {
        val sut = scheduler()
        val running = scope
        scope = null

        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = false)
        settle()
        assertThat(runs).isEmpty()

        scope = running
        sut.schedule(2_000, reloadBgData = false, triggeredByNewBG = false)
        settle()
        assertThat(runs).containsExactly(Run(2_000, reloadBgData = false, triggeredByNewBG = false))
    }

    /** After a run, the next change is a new one and runs on its own. */
    @Test
    fun `changes after a run start a new run`() = runTest {
        val sut = scheduler()

        sut.schedule(1_000, reloadBgData = false, triggeredByNewBG = true)
        settle()
        sut.schedule(5_000, reloadBgData = false, triggeredByNewBG = true)
        settle()

        assertThat(runs).containsExactly(
            Run(1_000, reloadBgData = false, triggeredByNewBG = true),
            Run(5_000, reloadBgData = false, triggeredByNewBG = true)
        ).inOrder()
    }
}
