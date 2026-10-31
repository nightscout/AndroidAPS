package app.aaps.desktop.shell.platform

import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.plugins.sync.nsclientV3.ws.NsLoadChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That a desktop load round stops where the other platforms stop.
 *
 * `DesktopNsLoadExecutor` used to compute each step's [WorkOutcome], log it, and run the whole
 * nine-step chain regardless. That was not cosmetic: a failed BG or treatments step still reached
 * `LoadDeviceStatusRunner`, which sets `initialLoadFinished = true` as its first statement, so the
 * round declared the catch-up finished. `NSClientV3Plugin.executeLoop` then returns early for the
 * rest of the session and the missed window is never fetched - and nothing shows for it, because a
 * later step clears the error. It heals only on the next socket reconnect.
 *
 * The executor itself needs nine graph-built runners and a coroutine scope, so what is checked here
 * is the decision it now delegates: the sequence of outcomes a round would walk, run through the
 * same [NsLoadChain] that iOS uses and that Android's WorkManager chain implements. If desktop ever
 * stops consulting it, the loop in `start()` no longer matches this.
 */
class DesktopNsLoadExecutorTest {

    /** How far a round gets through [outcomes], applying the shared rule after every step. */
    private fun stepsRun(outcomes: List<WorkOutcome>): Int {
        var run = 0
        for (outcome in outcomes) {
            run++
            if (!NsLoadChain.shouldContinue(outcome)) break
        }
        return run
    }

    @Test
    fun `a round stops at the first failing step`() {
        val outcomes = listOf(WorkOutcome.Success, WorkOutcome.Failure("network"), WorkOutcome.Success)

        // Two: the one that worked and the one that failed. The third never runs, which is the point -
        // on the real chain it would have been the step that marks the catch-up complete.
        assertEquals(2, stepsRun(outcomes))
    }

    @Test
    fun `a round runs every step when none fails`() {
        val outcomes = List(9) { WorkOutcome.Success }

        assertEquals(9, stepsRun(outcomes))
    }

    /** Skipped is a success with nothing to do; stopping there would abandon the round for no reason. */
    @Test
    fun `a skipped step does not stop the round`() {
        val outcomes = listOf(WorkOutcome.Success, WorkOutcome.Skipped("nothing to load"), WorkOutcome.Success)

        assertEquals(3, stepsRun(outcomes))
    }

    @Test
    fun `the rule the round uses is the shared one`() {
        assertTrue(NsLoadChain.shouldContinue(WorkOutcome.Skipped("nothing to load")))
        assertFalse(NsLoadChain.shouldContinue(WorkOutcome.Failure("x")))
        assertFalse(NsLoadChain.shouldContinue(WorkOutcome.Retry("later")))
    }
}
