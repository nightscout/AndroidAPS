package app.aaps.pump.carelevo.compose.patchflow

import kotlin.test.Test
import kotlin.test.assertEquals

/** The minutes the activation steps show as left of the 20 the patch gives. */
class CarelevoActivationTimeLimitTest {

    @Test
    fun `right after pairing all 20 minutes are left`() {
        assertEquals(20, activationMinutesLeft(deadline = 20 * MINUTE, now = 0))
    }

    @Test
    fun `a part of a minute counts as a whole minute`() {
        assertEquals(15, activationMinutesLeft(deadline = 15 * MINUTE, now = 1))
        assertEquals(1, activationMinutesLeft(deadline = MINUTE, now = MINUTE - 1))
    }

    @Test
    fun `nothing is left at the deadline or after it`() {
        assertEquals(0, activationMinutesLeft(deadline = MINUTE, now = MINUTE))
        assertEquals(0, activationMinutesLeft(deadline = MINUTE, now = 10 * MINUTE))
    }

    private companion object {

        const val MINUTE = 60_000L
    }
}
