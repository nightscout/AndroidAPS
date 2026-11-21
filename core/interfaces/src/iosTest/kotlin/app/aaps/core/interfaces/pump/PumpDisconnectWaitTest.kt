package app.aaps.core.interfaces.pump

import kotlin.test.Test
import kotlin.test.assertEquals

class PumpDisconnectWaitTest {

    /** Pins a decision: on iOS the queue closes the pump link at once, to save the background time of the wake. */
    @Test
    fun `on iOS the queue does not wait before the disconnect`() {
        assertEquals(0, defaultDisconnectWaitSeconds)
    }
}
