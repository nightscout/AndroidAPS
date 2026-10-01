package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That a client build makes no judgement about glucose quality.
 *
 * This looks like a test of a stub and is really a test of a decision. The reading a client shows
 * came from Nightscout, already judged by the master that owns the sensor; deciding again here would
 * mean disagreeing with the master about data this device never collected. So UNKNOWN is the answer,
 * and these pin it so that "finishing" the class later is a deliberate act rather than a tidy-up.
 */
class IosBgQualityCheckTest {

    private val check = IosBgQualityCheck()

    @Test
    fun `the state is unknown`() {
        assertEquals(BgQualityCheck.State.UNKNOWN, check.state)
    }

    /** The flow is what the overview badge collects, so it has to agree with the property. */
    @Test
    fun `the flow reports unknown too`() {
        assertEquals(BgQualityCheck.State.UNKNOWN, check.stateFlow.value)
    }

    /**
     * `state` is a `var` on the interface because `BgQualityCheckPlugin` writes it. That plugin does
     * not run on a client, and a write from anywhere else must not start a judgement this build has
     * no basis for.
     */
    @Test
    fun `writing a state does not change the answer`() {
        check.state = BgQualityCheck.State.DOUBLED

        assertEquals(BgQualityCheck.State.UNKNOWN, check.state)
        assertEquals(BgQualityCheck.State.UNKNOWN, check.stateFlow.value)
    }

    /** Empty, not a sentence: the badge it would describe is never shown on a client. */
    @Test
    fun `there is no description to show`() {
        assertEquals("", check.stateDescription())
    }
}
