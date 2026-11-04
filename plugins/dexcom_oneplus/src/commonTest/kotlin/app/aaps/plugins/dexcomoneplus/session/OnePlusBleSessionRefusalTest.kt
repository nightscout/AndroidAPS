package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.reconnect.OnePlusReconnectPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The session loop and the refusal count together: a sensor that refuses us is never asked a
 * fourth time, while an ordinary failure keeps the retry policy it always had.
 */
class OnePlusBleSessionRefusalTest {

    private val mac = "AA:BB:CC:DD:EE:FF"

    /** No waiting between attempts, and [maxAttempts] attempts in all. */
    private class QuickRetries(private val maxAttempts: Int) : OnePlusReconnectPolicy {
        override fun shouldRetry(attempt: Int, profile: OemDeviceProfile): Boolean = attempt < maxAttempts
        override fun nextDelayMs(attempt: Int, profile: OemDeviceProfile): Long = 0L
    }

    /** Answers every handshake with [result] and counts them. */
    private class CountingAuth(private val result: AuthResult) : OnePlusSessionAuth {
        var calls = 0
        override fun authenticate(pairingCode: String, savedSharedKey: ByteArray?): AuthResult {
            calls++
            return result
        }
    }

    private fun session(
        auth: OnePlusSessionAuth,
        ledger: OnePlusAuthRefusalLedger,
        states: MutableList<OnePlusWarmupState>,
    ) = OnePlusBleSessionSkeleton(
        gatt = FakeOnePlusGatt(),
        auth = auth,
        reconnectPolicy = QuickRetries(maxAttempts = 10),
        profile = DeviceProfileRegistry.GenericFallback,
        onWarmup = { states.add(it) },
        requestNewSensorStart = true,
        refusalLedger = ledger,
        // No pause, so the test does not wait 15 minutes between refusals. The count is what matters.
        refusalPolicy = OnePlusAuthRefusalPolicy(pauseMs = 0L),
    )

    @Test
    fun `three refusals in a row stop the session before the fourth`() {
        val auth = CountingAuth(AuthResult(ok = false, message = "ONEPLUS_AUTH: refused", refusedBySensor = true))
        val ledger = OnePlusAuthRefusalLedgerInMemory()
        val states = mutableListOf<OnePlusWarmupState>()
        session(auth, ledger, states).startWithPairingCode(mac, "1234")

        assertEquals(OnePlusAuthRefusalPolicy.MAX_REFUSALS_IN_A_ROW, auth.calls)
        assertEquals(3, ledger.load().count)
        val last = states.last()
        assertEquals(OnePlusWarmupState.Phase.FAILED, last.phase)
        assertTrue(last.message.orEmpty().startsWith(OnePlusAuthRefusalPolicy.MESSAGE_LOCKED))
    }

    @Test
    fun `a sensor that already refused three times is not asked at all`() {
        val auth = CountingAuth(AuthResult(ok = false, refusedBySensor = true))
        val ledger = OnePlusAuthRefusalLedgerInMemory(OnePlusAuthRefusals(address = mac, count = 3, lastAtMs = 1L))
        val states = mutableListOf<OnePlusWarmupState>()
        session(auth, ledger, states).startWithPairingCode(mac, "1234")

        assertEquals(0, auth.calls)
        assertEquals(OnePlusWarmupState.Phase.FAILED, states.last().phase)
    }

    @Test
    fun `an ordinary failure is not a refusal and keeps the retry budget`() {
        // A timeout: not the sensor saying no. All ten attempts are used, as before this rule.
        val auth = CountingAuth(AuthResult(ok = false, message = "ONEPLUS_AUTH: notify timeout step=0"))
        val ledger = OnePlusAuthRefusalLedgerInMemory()
        val states = mutableListOf<OnePlusWarmupState>()
        session(auth, ledger, states).startWithPairingCode(mac, "1234")

        assertEquals(10, auth.calls)
        assertEquals(0, ledger.load().count)
        assertTrue(states.last().message.orEmpty().startsWith("ONEPLUS_RECONNECT_EXHAUSTED"))
    }
}
