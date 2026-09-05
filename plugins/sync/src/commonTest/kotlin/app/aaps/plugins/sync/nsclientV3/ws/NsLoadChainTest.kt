package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.objects.workflow.WorkOutcome
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a Nightscout load round carries on.
 *
 * Worth its own test because the first version of the executor got it wrong: it continued only on
 * `Success`, which quietly abandoned the rest of the round whenever a step had nothing to do. The
 * type's own documentation is the specification - skipped "must not fail a chain".
 */
class NsLoadChainTest {

    @Test
    fun `a successful step carries on`() {
        assertTrue(NsLoadChain.shouldContinue(WorkOutcome.Success))
    }

    /**
     * The case the first version got wrong.
     *
     * A step with nothing to load reports Skipped. Treating that as a stop meant one quiet step
     * could cancel every step after it - so a round that found no new treatments would also never
     * fetch device status.
     */
    @Test
    fun `a skipped step carries on`() {
        assertTrue(NsLoadChain.shouldContinue(WorkOutcome.Skipped("nothing to load")))
    }

    @Test
    fun `a failed step stops the round`() {
        assertFalse(NsLoadChain.shouldContinue(WorkOutcome.Failure("network down")))
    }

    /** Both named failures are failures, not a third state. */
    @Test
    fun `the named failures stop the round too`() {
        assertFalse(NsLoadChain.shouldContinue(WorkOutcome.Stopped))
        assertFalse(NsLoadChain.shouldContinue(WorkOutcome.StaleInput))
    }
}
