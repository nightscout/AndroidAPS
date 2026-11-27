package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.reconnect.OnePlusReconnectPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the watch waits for the sensor's next cycle (`standingConnectWait`): with one standing connect
 * and no scan, where the phone scans all the time.
 *
 * Measured on a Galaxy Watch 5 with the scan wait: 34 minutes of Bluetooth scan in one hour, 18 % of
 * the battery. A watch that never heard its sensor was empty in 6 hours.
 */
class OnePlusBleSessionStandingConnectTest {

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

    /** Every `autoConnect` ends without the sensor, as a standing connect does after a silent cycle. */
    private class SilentSensorGatt(
        private val real: FakeOnePlusGatt,
        private val stopAfterStandingConnects: Int,
    ) : OnePlusGattClient by real {

        lateinit var session: OnePlusBleSession
        var standingConnects = 0

        override fun connect(deviceAddress: String, autoConnect: Boolean) {
            real.connect(deviceAddress, autoConnect)
            if (!autoConnect) return
            standingConnects++
            if (standingConnects >= stopAfterStandingConnects) session.stop("test done")
            error("ONEPLUS_GATT: connect/discover timeout 630000ms")
        }
    }

    private class Scans(private val fresh: Boolean) {

        val waits = mutableListOf<Long>()

        fun beforeConnect(attempt: Int, scanMs: Long?): OnePlusConnectPrep {
            if (scanMs != null) waits += scanMs
            // The first connect of the session comes from a sighting; after that the answer is the test's.
            return OnePlusConnectPrep(advFresh = attempt == 0 || fresh)
        }
    }

    /** A start with the sensor asleep: nothing at the first look, then seen in window [seenInWindow] (0 = never). */
    private class StartupScans(private val seenInWindow: Int) {

        val waits = mutableListOf<Long>()

        fun beforeConnect(scanMs: Long?): OnePlusConnectPrep {
            if (scanMs == null) return OnePlusConnectPrep(advFresh = false)
            waits += scanMs
            return OnePlusConnectPrep(advFresh = waits.size == seenInWindow)
        }
    }

    private fun session(
        gatt: OnePlusGattClient,
        auth: OnePlusSessionAuth,
        standing: Boolean,
        startupScanMs: Long = OnePlusBleSessionCyclePolicy.STANDING_CONNECT_STARTUP_SCAN_MS,
        beforeConnect: (attempt: Int, scanMs: Long?) -> OnePlusConnectPrep,
    ) =
        OnePlusBleSessionSkeleton(
            gatt = gatt,
            auth = auth,
            reconnectPolicy = NoRetryPause,
            profile = DeviceProfileRegistry.Watch,
            requestNewSensorStart = false,
            beforeConnect = { _, attempt, scanMs -> beforeConnect(attempt, scanMs) },
            postCycleAdvGuardMs = 1L,
            standingConnectWait = standing,
            standingConnectMinGapMs = 1L,
            standingConnectStartupScanMs = startupScanMs,
        )

    private fun session(gatt: OnePlusGattClient, auth: OnePlusSessionAuth, scans: Scans, standing: Boolean) =
        session(gatt, auth, standing) { attempt, scanMs -> scans.beforeConnect(attempt, scanMs) }

    @Test
    fun `after a start the watch scans until it sees the sensor and places no standing connect`() {
        val gatt = FakeOnePlusGatt()
        val auth = FailingAuth(stopAt = 1)
        val scans = StartupScans(seenInWindow = 3)
        val session = session(gatt, auth, standing = true) { _, scanMs -> scans.beforeConnect(scanMs) }
        auth.session = session

        session.startWithPairingCode(sensor, "1234")

        // Three scan windows, one after the other, then a direct connect. No standing connect.
        assertEquals(List(3) { OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS }, scans.waits)
        assertEquals(listOf(false), gatt.autoConnects)
    }

    @Test
    fun `a watch that does not find its sensor at the start stops scanning and places a standing connect`() {
        val gatt = FakeOnePlusGatt()
        val auth = FailingAuth(stopAt = 1)
        val scans = StartupScans(seenInWindow = 0)
        // The start-up scan time is over at once.
        val session = session(gatt, auth, standing = true, startupScanMs = 0L) { _, scanMs -> scans.beforeConnect(scanMs) }
        auth.session = session

        session.startWithPairingCode(sensor, "1234")

        assertEquals(listOf(true), gatt.autoConnects)
        assertEquals(emptyList(), scans.waits)
    }

    @Test
    fun `after the sensor closed the link the watch places a standing connect and does not scan`() {
        val gatt = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val auth = FailingAuth(stopAt = 2)
        val scans = Scans(fresh = true)
        val session = session(gatt, auth, scans, standing = true)
        auth.session = session

        session.startWithPairingCode(sensor, "1234")

        assertEquals(listOf(false, true), gatt.autoConnects)
        assertEquals(emptyList(), scans.waits)
    }

    @Test
    fun `the phone still scans for the next cycle`() {
        val gatt = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val auth = FailingAuth(stopAt = 2)
        val scans = Scans(fresh = true)
        val session = session(gatt, auth, scans, standing = false)
        auth.session = session

        session.startWithPairingCode(sensor, "1234")

        assertEquals(listOf(false, false), gatt.autoConnects)
        assertEquals(listOf(OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS), scans.waits)
    }

    @Test
    fun `a standing connect that ended without the sensor is followed by one scan window and the next standing connect`() {
        val real = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val gatt = SilentSensorGatt(real, stopAfterStandingConnects = 3)
        val auth = FailingAuth(stopAt = Int.MAX_VALUE)
        val scans = Scans(fresh = false)
        val session = session(gatt, auth, scans, standing = true)
        auth.session = session
        gatt.session = session

        session.startWithPairingCode(sensor, "1234")

        // First link from a sighting, then three standing connects.
        assertEquals(listOf(false, true, true, true), real.autoConnects)
        // One scan window between two standing connects, never more: two gaps, two windows.
        assertEquals(
            listOf(OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS, OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS),
            scans.waits,
        )
    }

    @Test
    fun `a sensor seen in the scan window is connected to directly`() {
        val real = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val gatt = SilentSensorGatt(real, stopAfterStandingConnects = Int.MAX_VALUE)
        // Calls: 1 after the first link, 2 after the direct connect that follows the sighting.
        val auth = FailingAuth(stopAt = 2)
        val scans = Scans(fresh = true)
        val session = session(gatt, auth, scans, standing = true)
        auth.session = session
        gatt.session = session

        session.startWithPairingCode(sensor, "1234")

        // Link, standing connect without the sensor, one window that sees it, a direct connect.
        assertEquals(listOf(false, true, false), real.autoConnects)
        assertEquals(listOf(OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS), scans.waits)
    }

    @Test
    fun `the watch profile lets a standing connect wait for two sensor cycles`() {
        assertEquals(630_000L, DeviceProfileRegistry.Watch.autoConnectTimeoutMs)
        assertEquals(
            DeviceProfileRegistry.GenericFallback,
            DeviceProfileRegistry.Watch.copy(autoConnectTimeoutMs = DeviceProfileRegistry.GenericFallback.autoConnectTimeoutMs),
        )
    }
}
