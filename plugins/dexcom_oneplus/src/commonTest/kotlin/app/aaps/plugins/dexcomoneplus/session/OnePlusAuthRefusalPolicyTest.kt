package app.aaps.plugins.dexcomoneplus.session

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule that keeps a G7 / ONE+ from locking itself: four refusals in a row lock it, so we stop at
 * three and wait between them.
 */
class OnePlusAuthRefusalPolicyTest {

    private val pause = 15L * 60L * 1000L
    private val policy = OnePlusAuthRefusalPolicy(pauseMs = pause)
    private val mac = "AA:BB:CC:DD:EE:FF"
    private val t0 = 1_000_000_000L

    private fun refusedTimes(times: Int, savedKey: Boolean = false): OnePlusAuthRefusals {
        var state = OnePlusAuthRefusals()
        repeat(times) { i -> state = policy.afterRefusal(state, mac, t0 + i * pause, savedKey) }
        return state
    }

    @Test
    fun `a sensor that never refused us may be asked`() {
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(OnePlusAuthRefusals(), mac, t0))
    }

    @Test
    fun `after a refusal we wait the pause before asking again`() {
        val state = policy.afterRefusal(OnePlusAuthRefusals(), mac, t0, withSavedKey = false)
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Wait(t0 + pause), policy.decide(state, mac, t0 + 1_000L))
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(state, mac, t0 + pause))
    }

    @Test
    fun `a refused saved key may go to the full pairing at once`() {
        val state = policy.afterRefusal(OnePlusAuthRefusals(), mac, t0, withSavedKey = true)
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(state, mac, t0 + 1L))
    }

    @Test
    fun `we stop one refusal before the one that locks the sensor`() {
        assertEquals(3, OnePlusAuthRefusalPolicy.MAX_REFUSALS_IN_A_ROW)
        val two = refusedTimes(2)
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(two, mac, t0 + 10 * pause))
        val three = refusedTimes(3)
        // However long we wait, three in a row is the end until the user starts again.
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Locked, policy.decide(three, mac, t0 + 1_000 * pause))
    }

    @Test
    fun `a saved key refusal still counts toward the stop`() {
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Locked, policy.decide(refusedTimes(3, savedKey = true), mac, t0))
    }

    @Test
    fun `a success clears the count`() {
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(policy.afterSuccess(), mac, t0))
    }

    @Test
    fun `refusals of another sensor do not count for this sensor`() {
        val other = refusedTimes(3)
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(other, "11:22:33:44:55:66", t0))
        val first = policy.afterRefusal(other, "11:22:33:44:55:66", t0, withSavedKey = false)
        assertEquals(1, first.count)
    }

    @Test
    fun `the address is matched without case`() {
        val state = refusedTimes(3)
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Locked, policy.decide(state, mac.lowercase(), t0))
    }

    @Test
    fun `the user starting another code begins a fresh count`() {
        val after = policy.afterUserStart(refusedTimes(3), mac, codeChanged = true)
        assertEquals(OnePlusAuthRefusals(), after)
    }

    @Test
    fun `the user starting another sensor begins a fresh count`() {
        val after = policy.afterUserStart(refusedTimes(3), "11:22:33:44:55:66", codeChanged = false)
        assertEquals(OnePlusAuthRefusals(), after)
    }

    @Test
    fun `the same code on the same sensor gets one more try and not before the pause`() {
        val locked = refusedTimes(3)
        val after = policy.afterUserStart(locked, mac, codeChanged = false)
        assertEquals(2, after.count)
        // Still the pause after the last refusal: pressing Connect again and again is not a way round it.
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Wait(locked.lastAtMs + pause), policy.decide(after, mac, locked.lastAtMs))
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Allowed, policy.decide(after, mac, locked.lastAtMs + pause))
        // And the next refusal stops us again.
        val again = policy.afterRefusal(after, mac, locked.lastAtMs + pause, withSavedKey = false)
        assertEquals(OnePlusAuthRefusalPolicy.Decision.Locked, policy.decide(again, mac, locked.lastAtMs + 100 * pause))
    }
}
