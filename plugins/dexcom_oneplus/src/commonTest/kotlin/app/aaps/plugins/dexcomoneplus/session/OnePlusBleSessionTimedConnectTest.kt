package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.reconnect.OnePlusReconnectPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The watch's timed connect (`wakeAlarm`): after a reading it sleeps with nothing in place, wakes a
 * little before the sensor's next cycle and connects directly.
 *
 * Why: a standing connect alone listens too little. On a Galaxy Watch 5 the watch missed 10 of 146
 * readings with it, and in every missed round its log was empty.
 */
class OnePlusBleSessionTimedConnectTest {

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

    private class FakeWakeAlarm(private val canWake: Boolean = true) : OnePlusWakeAlarm {

        val wakeAts = mutableListOf<Long>()

        override fun wakeAt(epochMs: Long): Boolean {
            if (canWake) wakeAts += epochMs
            return canWake
        }

        override fun cancel() = Unit
    }

    /** The connects with an index in [failing] end without the sensor; the session stops at connect [stopAt]. */
    private class GattWithSilentConnects(
        private val real: FakeOnePlusGatt,
        private val failing: IntRange,
        private val stopAt: Int,
        private val onConnect: () -> Unit = {},
    ) : OnePlusGattClient by real {

        lateinit var session: OnePlusBleSession

        override fun connect(deviceAddress: String, autoConnect: Boolean) {
            onConnect()
            real.connect(deviceAddress, autoConnect)
            if (real.connects >= stopAt) session.stop("test done")
            if (real.connects in failing) error("ONEPLUS_GATT: link down status=133")
        }
    }

    /** Counts the holds of the length of the timed connect's own lock that are not given back yet. */
    private class CountingLease : OnePlusCpuLease {

        var timedHoldsOpen = 0

        override fun hold(maxMillis: Long): OnePlusCpuLease.Hold? {
            if (maxMillis != OnePlusBleSessionCyclePolicy.TIMED_CONNECT_WAKE_LOCK_MS) return null
            timedHoldsOpen++
            return object : OnePlusCpuLease.Hold {
                private var held = true
                override fun release() {
                    if (held) timedHoldsOpen--
                    held = false
                }
            }
        }
    }

    private fun session(
        gatt: OnePlusGattClient,
        auth: OnePlusSessionAuth,
        alarm: OnePlusWakeAlarm?,
        cpuLease: OnePlusCpuLease? = null,
    ) =
        OnePlusBleSessionSkeleton(
            cpuLease = cpuLease,
            gatt = gatt,
            auth = auth,
            reconnectPolicy = NoRetryPause,
            profile = DeviceProfileRegistry.Watch,
            requestNewSensorStart = false,
            // The first connect of the session comes from a sighting.
            beforeConnect = { _, attempt, _ -> OnePlusConnectPrep(advFresh = attempt == 0) },
            postCycleAdvGuardMs = 1L,
            standingConnectWait = true,
            standingConnectMinGapMs = 1L,
            wakeAlarm = alarm,
            // Short, but long enough that a slow test machine is not past the moment before it is planned.
            sensorCycleMs = CYCLE_MS,
            timedConnectLeadMs = LEAD_MS,
        )

    private companion object {

        const val CYCLE_MS = 1_000L
        const val LEAD_MS = 600L
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    @Test
    fun `after a reading the watch sleeps until the sensor is due and connects directly`() {
        val gatt = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val auth = FailingAuth(stopAt = 2)
        val alarm = FakeWakeAlarm()
        val session = session(gatt, auth, alarm)
        auth.session = session
        val readingAt = now()
        session.noteReading(readingAt)

        session.startWithPairingCode(sensor, "1234")

        // No standing connect: the second connect is the timed one, and it is direct.
        assertEquals(listOf(false, false), gatt.autoConnects)
        // The alarm was set for one cycle after the reading, less the lead.
        assertEquals(listOf(readingAt + CYCLE_MS - LEAD_MS), alarm.wakeAts)
    }

    @Test
    fun `a timed connect that failed before the link came up is placed again directly`() {
        val real = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val gatt = GattWithSilentConnects(real, failing = 2..2, stopAt = 3)
        val auth = FailingAuth(stopAt = Int.MAX_VALUE)
        val session = session(gatt, auth, FakeWakeAlarm())
        auth.session = session
        gatt.session = session
        session.noteReading(now())

        session.startWithPairingCode(sensor, "1234")

        // Link from a sighting, the timed connect, then the same connect again: no standing connect.
        assertEquals(listOf(false, false, false), real.autoConnects)
    }

    @Test
    fun `when every retry of the timed connect failed the standing connect follows`() {
        val real = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val retries = OnePlusBleSessionCyclePolicy.TIMED_CONNECT_RETRIES
        // Connect 1 is the link from a sighting, connect 2 the timed connect, then its retries.
        val gatt = GattWithSilentConnects(real, failing = 2..(2 + retries), stopAt = 3 + retries)
        val auth = FailingAuth(stopAt = Int.MAX_VALUE)
        val session = session(gatt, auth, FakeWakeAlarm())
        auth.session = session
        gatt.session = session
        session.noteReading(now())

        session.startWithPairingCode(sensor, "1234")

        assertEquals(listOf(false) + List(1 + retries) { false } + listOf(true), real.autoConnects)
    }

    /**
     * In a full train the watch fell asleep between two tries: they came 4.5 s, 7.6 s and 11.9 s
     * apart, and the last one was placed when the sensor was asleep again.
     */
    @Test
    fun `the watch stays awake from the wake-up until the last retry of the timed connect`() {
        val real = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val retries = OnePlusBleSessionCyclePolicy.TIMED_CONNECT_RETRIES
        val lease = CountingLease()
        val heldAtConnect = mutableListOf<Int>()
        val gatt = GattWithSilentConnects(real, failing = 2..(2 + retries), stopAt = 3 + retries) {
            heldAtConnect += lease.timedHoldsOpen
        }
        val auth = FailingAuth(stopAt = Int.MAX_VALUE)
        val session = session(gatt, auth, FakeWakeAlarm(), lease)
        auth.session = session
        gatt.session = session
        session.noteReading(now())

        session.startWithPairingCode(sensor, "1234")

        // Not held for the link from a sighting, held for the timed connect and each retry, and
        // given back before the standing connect, which may wait for minutes.
        assertEquals(listOf(0) + List(1 + retries) { 1 } + listOf(0), heldAtConnect)
    }

    @Test
    fun `the lock of the timed connect is given back when the link comes up`() {
        val gatt = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val lease = CountingLease()
        val heldAtAuth = mutableListOf<Int>()
        val auth = object : OnePlusSessionAuth {
            lateinit var session: OnePlusBleSession
            override fun authenticate(pairingCode: String, savedSharedKey: ByteArray?): AuthResult {
                heldAtAuth += lease.timedHoldsOpen
                if (heldAtAuth.size >= 2) session.stop("test done")
                return AuthResult(ok = false, message = "ONEPLUS_AUTH: GATT disconnected")
            }
        }
        val session = session(gatt, auth, FakeWakeAlarm(), lease)
        auth.session = session
        session.noteReading(now())

        session.startWithPairingCode(sensor, "1234")

        // The second handshake follows the timed connect: its link is up, so the lock is back.
        assertEquals(listOf(0, 0), heldAtAuth)
    }

    @Test
    fun `a timed connect is placed again only before the link came up and only a few times`() {
        val planned = 1_000_000L
        val soon = planned + 3_000L
        assertTrue(OnePlusBleSessionCyclePolicy.retryTimedConnect(planned, soon, retriesDone = 0, sensorReached = false))
        // The failed connect was not a timed one.
        assertFalse(OnePlusBleSessionCyclePolicy.retryTimedConnect(0L, soon, retriesDone = 0, sensorReached = false))
        // The link came up: the handshake rules decide.
        assertFalse(OnePlusBleSessionCyclePolicy.retryTimedConnect(planned, soon, retriesDone = 0, sensorReached = true))
        assertFalse(
            OnePlusBleSessionCyclePolicy.retryTimedConnect(
                planned, soon, retriesDone = OnePlusBleSessionCyclePolicy.TIMED_CONNECT_RETRIES, sensorReached = false,
            ),
        )
        // The sensor is asleep again.
        assertFalse(OnePlusBleSessionCyclePolicy.retryTimedConnect(planned, planned + 20_001L, retriesDone = 0, sensorReached = false))
    }

    @Test
    fun `a watch that cannot set an exact alarm waits with a standing connect`() {
        val gatt = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val auth = FailingAuth(stopAt = 2)
        val session = session(gatt, auth, FakeWakeAlarm(canWake = false))
        auth.session = session
        session.noteReading(now())

        session.startWithPairingCode(sensor, "1234")

        assertEquals(listOf(false, true), gatt.autoConnects)
    }

    @Test
    fun `without a reading nothing is timed and the standing connect waits`() {
        val gatt = FakeOnePlusGatt().apply { linkDownStatus = OnePlusBleSessionCyclePolicy.GATT_STATUS_PEER_TERMINATED }
        val auth = FailingAuth(stopAt = 2)
        val alarm = FakeWakeAlarm()
        val session = session(gatt, auth, alarm)
        auth.session = session

        session.startWithPairingCode(sensor, "1234")

        assertEquals(listOf(false, true), gatt.autoConnects)
        assertEquals(emptyList(), alarm.wakeAts)
    }

    @Test
    fun `the timed connect is planned one cycle after the reading less the lead`() {
        val reading = 1_000_000L
        assertEquals(
            reading + 300_000L - 4_000L,
            OnePlusBleSessionCyclePolicy.timedConnectAtMs(newestReadingAtMs = reading, nowMs = reading + 20_000L),
        )
    }

    @Test
    fun `nothing is planned without a reading or from a reading of an older cycle`() {
        assertNull(OnePlusBleSessionCyclePolicy.timedConnectAtMs(newestReadingAtMs = 0L, nowMs = 1_000_000L))
        // The cycle that just ended brought no new reading: the planned moment is already over.
        assertNull(OnePlusBleSessionCyclePolicy.timedConnectAtMs(newestReadingAtMs = 1_000_000L, nowMs = 1_000_000L + 310_000L))
        // A reading time ahead of the clock cannot be trusted.
        assertNull(OnePlusBleSessionCyclePolicy.timedConnectAtMs(newestReadingAtMs = 2_000_000L, nowMs = 1_000_000L))
    }

    @Test
    fun `a watch that woke after the sensor went back to sleep does not place the timed connect`() {
        val planned = 1_000_000L
        assertTrue(OnePlusBleSessionCyclePolicy.timedConnectStillInTime(planned, nowMs = planned + 3_000L))
        assertTrue(OnePlusBleSessionCyclePolicy.timedConnectStillInTime(planned, nowMs = planned + 20_000L))
        assertFalse(OnePlusBleSessionCyclePolicy.timedConnectStillInTime(planned, nowMs = planned + 20_001L))
    }
}
