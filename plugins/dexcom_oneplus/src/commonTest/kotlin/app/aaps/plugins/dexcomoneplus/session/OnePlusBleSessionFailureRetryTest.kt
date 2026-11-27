package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.reconnect.OnePlusReconnectPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The retry after a cycle that reached the sensor and then failed, on an Android phone that waits for
 * advertisements (GENERIC_FALLBACK).
 *
 * Field report 2026-11-14 (Cubot, snoop log): when the sensor closes the link itself (GATT status 19)
 * it sleeps until its next cycle, so an autoConnect parked at once cannot succeed. After a lost link
 * (status 8) the sensor is sometimes still awake, and the immediate park saved the cycle.
 */
class OnePlusBleSessionFailureRetryTest {

    private val sensor = "AA:BB:CC:DD:EE:FF"

    private object NoRetryPause : OnePlusReconnectPolicy {
        override fun shouldRetry(attempt: Int, profile: OemDeviceProfile): Boolean = true
        override fun nextDelayMs(attempt: Int, profile: OemDeviceProfile): Long = 0L
    }

    /** The link comes up, then every handshake fails; the session stops at call [stopAt]. */
    private class FailingAuth(private val stopAt: Int) : OnePlusSessionAuth {
        var calls = 0
        lateinit var session: OnePlusBleSession
        override fun authenticate(pairingCode: String, savedSharedKey: ByteArray?): AuthResult {
            calls++
            if (calls >= stopAt) session.stop("test done")
            return AuthResult(ok = false, message = "ONEPLUS_AUTH: GATT disconnected")
        }
    }

    private class Run(val gatt: FakeOnePlusGatt, val scanWaits: List<Long>)

    private fun run(linkDownStatus: Int?): Run {
        val gatt = FakeOnePlusGatt().apply { this.linkDownStatus = linkDownStatus }
        val auth = FailingAuth(stopAt = 2)
        val scanWaits = mutableListOf<Long>()
        val session = OnePlusBleSessionSkeleton(
            gatt = gatt,
            auth = auth,
            reconnectPolicy = NoRetryPause,
            profile = DeviceProfileRegistry.GenericFallback,
            requestNewSensorStart = false,
            beforeConnect = { _, _, scanMs ->
                if (scanMs != null) scanWaits += scanMs
                OnePlusConnectPrep(advFresh = true)
            },
            postCycleAdvGuardMs = 1L,
        )
        auth.session = session
        session.startWithPairingCode(sensor, "1234")
        return Run(gatt, scanWaits)
    }

    @Test
    fun `after the sensor closed the link the session waits for its next cycle instead of parking an autoConnect`() {
        val run = run(linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED)

        // Second connect comes from a fresh advertisement (hard connect), after a scan wait.
        assertEquals(listOf(false, false), run.gatt.autoConnects)
        assertEquals(listOf(OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS), run.scanWaits)
    }

    @Test
    fun `after a lost link the session still parks an autoConnect at once`() {
        val run = run(linkDownStatus = 8)

        assertEquals(listOf(false, true), run.gatt.autoConnects)
        assertEquals(emptyList(), run.scanWaits)
    }

    @Test
    fun `with no link down status the immediate autoConnect stays as before`() {
        val run = run(linkDownStatus = null)

        assertEquals(listOf(false, true), run.gatt.autoConnects)
        assertEquals(emptyList(), run.scanWaits)
    }

    /** The handshake throws, as a write does when the sensor drops the link in the middle of it. */
    private class ThrowingAuth(private val stopAt: Int) : OnePlusSessionAuth {
        var calls = 0
        lateinit var session: OnePlusBleSession
        override fun authenticate(pairingCode: String, savedSharedKey: ByteArray?): AuthResult {
            calls++
            if (calls >= stopAt) session.stop("test done")
            error("ONEPLUS_GATT: write Auth status=257")
        }
    }

    private fun sessionWith(gatt: FakeOnePlusGatt, auth: OnePlusSessionAuth, errors: MutableList<String>) =
        OnePlusBleSessionSkeleton(
            gatt = gatt,
            auth = auth,
            reconnectPolicy = NoRetryPause,
            profile = DeviceProfileRegistry.GenericFallback,
            requestNewSensorStart = false,
            onError = { message, _ -> errors += message },
            beforeConnect = { _, _, _ -> OnePlusConnectPrep(advFresh = true) },
            postCycleAdvGuardMs = 1L,
        )

    @Test
    fun `a handshake that throws is a failed cycle and the session connects again`() {
        val gatt = FakeOnePlusGatt()
        val auth = ThrowingAuth(stopAt = 2)
        val errors = mutableListOf<String>()
        val session = sessionWith(gatt, auth, errors)
        auth.session = session

        // Before the fix the exception left startWithPairingCode here, after one connect.
        session.startWithPairingCode(sensor, "1234")

        assertEquals(2, gatt.connects)
        assertEquals(listOf("ONEPLUS_GATT: write Auth status=257"), errors)
    }

    @Test
    fun `a session that ends by an exception does not leave its link open`() {
        val gatt = FakeOnePlusGatt()
        val auth = object : OnePlusSessionAuth {
            override fun authenticate(pairingCode: String, savedSharedKey: ByteArray?): AuthResult =
                AuthResult(ok = false, message = "ONEPLUS_AUTH: GATT disconnected")
        }
        // The callback of the session throws, so the exception leaves the session.
        val session = OnePlusBleSessionSkeleton(
            gatt = gatt,
            auth = auth,
            reconnectPolicy = NoRetryPause,
            profile = DeviceProfileRegistry.GenericFallback,
            requestNewSensorStart = false,
            onError = { _, _ -> error("a watcher failed") },
            beforeConnect = { _, _, _ -> OnePlusConnectPrep(advFresh = true) },
            postCycleAdvGuardMs = 1L,
        )

        assertFailsWith<IllegalStateException> { session.startWithPairingCode(sensor, "1234") }

        assertEquals(1, gatt.connects)
        assertFalse(gatt.connected)
    }
}
