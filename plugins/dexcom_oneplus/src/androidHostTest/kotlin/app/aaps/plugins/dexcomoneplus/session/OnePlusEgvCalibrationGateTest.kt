package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusKeksNotify
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateTx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationBoundsRx
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The Control loop may build opcode 0x34 only after a fingerstick has been taken from the queue,
 * the checks at send time have passed and the transmitter clock is known. An empty queue, a failed
 * check, or a clock that is still unknown, writes nothing of that opcode.
 */
class OnePlusEgvCalibrationGateTest {

    @Test
    fun `an empty queue writes no calibrate frame`() {
        val gatt = RecordingGatt()
        val session = OnePlusEgvSession(gatt = gatt, calibrationQueue = null)

        runOneTurn(session)

        assertThat(calibrateFrames(gatt)).isEmpty()
    }

    @Test
    fun `a queued value stays queued and unsent while the transmitter clock is unknown`() {
        val gatt = RecordingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val session = OnePlusEgvSession(gatt = gatt, calibrationQueue = queue, calibrationSendCheck = allowAll)

        runOneTurn(session)

        assertThat(calibrateFrames(gatt)).isEmpty()
        assertThat(queue.peek()?.glucoseMgdl).isEqualTo(150)
    }

    @Test
    fun `a known transmitter clock writes opcode 0x34 and then the slot is empty`() {
        val gatt = RecordingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val session = OnePlusEgvSession(gatt = gatt, calibrationQueue = queue, calibrationSendCheck = allowAll)
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        runOneTurn(session)

        val frames = calibrateFrames(gatt)
        assertThat(frames).hasSize(1)
        assertThat(frames.single()[0]).isEqualTo(OnePlusCalibrateTx.OPCODE)
        assertThat(queue.peek()).isNull()
    }

    @Test
    fun `a write that throws leaves no frame and reports no acceptance`() {
        val gatt = ThrowingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val results = mutableListOf<OnePlusCalibrateRx?>()
        val session = OnePlusEgvSession(
            gatt = gatt,
            calibrationQueue = queue,
            onCalibrationResult = { results += it },
            calibrationSendCheck = allowAll,
        )
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        runOneTurn(session)

        assertThat(gatt.writes).isEmpty()
        assertThat(queue.peek()).isNull()
        assertThat(results).containsExactly(null)
    }

    @Test
    fun `a lost answer is settled by the calibration state and the value is never sent twice`() {
        val now = System.currentTimeMillis()
        // Blood 60 s ago, clock read 30 s ago at 10 000 s: the value is for sensor second 9 970.
        val gatt = ScriptedGatt(statePacket = boundsPacket(glucose = 150, timeSeconds = 9_970))
        val queue = OnePlusCalibrationQueue()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val answers = mutableListOf<OnePlusCalibrateRx?>()
        val states = mutableListOf<Pair<OnePlusCalibrationBoundsRx, Boolean>>()
        val session = OnePlusEgvSession(
            gatt = gatt,
            calibrationQueue = queue,
            onCalibrationResult = { answers += it },
            onCalibrationState = { state, matches -> states += state to matches },
            calibrationSendCheck = allowAll,
        )
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        // The calibration exchange asks shouldContinue several times; a single turn would stop it early.
        runTurns(session, calls = 12)

        assertThat(answers).containsExactly(null)
        assertThat(states).hasSize(1)
        assertThat(states.single().second).isTrue()
        assertThat(states.single().first.processing).isEqualTo(OnePlusCalibrationBoundsRx.Processing.IN_PROGRESS)
        assertThat(gatt.writes.count { it[0] == OnePlusCalibrateTx.OPCODE }).isEqualTo(1)
        assertThat(gatt.writes.count { it[0] == OnePlusCalibrationBoundsRx.OPCODE }).isEqualTo(1)
    }

    @Test
    fun `a sensor back in warm-up at send time drops the value as not sent, before any write`() {
        val gatt = RecordingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val stable = OnePlusGlucoseSample(mgdl = 150.0, timestampMs = now - 60_000L, sequence = 1L, trendSlopeMgdlPerMin = 0.5)
        val answers = mutableListOf<OnePlusCalibrateRx?>()
        val notSent = mutableListOf<OnePlusCalibrationOutcome.NotSent.Reason>()
        val session = OnePlusEgvSession(
            gatt = gatt,
            calibrationQueue = queue,
            onCalibrationResult = { answers += it },
            // The real checks, on a sensor that went back to warm-up after the user confirmed.
            calibrationSendCheck = { glucose, bloodAt, nowMs ->
                OnePlusCalibrationGate.check(glucose, bloodAt, nowMs, OnePlusWarmupState.Phase.WARMING, stable)
            },
            onCalibrationNotSent = { notSent += it },
        )
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        runOneTurn(session)

        assertThat(calibrateFrames(gatt)).isEmpty()
        assertThat(queue.peek()).isNull()
        assertThat(notSent).containsExactly(OnePlusCalibrationOutcome.NotSent.Reason.NOT_READY)
        // Nothing went out, so it is not "unknown".
        assertThat(answers).isEmpty()
    }

    @Test
    fun `glucose changing fast at send time drops the value as not sent`() {
        val gatt = RecordingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val fast = OnePlusGlucoseSample(mgdl = 150.0, timestampMs = now - 60_000L, sequence = 1L, trendSlopeMgdlPerMin = 3.5)
        val notSent = mutableListOf<OnePlusCalibrationOutcome.NotSent.Reason>()
        val session = OnePlusEgvSession(
            gatt = gatt,
            calibrationQueue = queue,
            calibrationSendCheck = { glucose, bloodAt, nowMs ->
                OnePlusCalibrationGate.check(glucose, bloodAt, nowMs, OnePlusWarmupState.Phase.READY, fast)
            },
            onCalibrationNotSent = { notSent += it },
        )
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        runOneTurn(session)

        assertThat(calibrateFrames(gatt)).isEmpty()
        assertThat(notSent).containsExactly(OnePlusCalibrationOutcome.NotSent.Reason.CHANGING_TOO_FAST)
    }

    @Test
    fun `a value over one hour old is dropped as not sent, never as unknown`() {
        val gatt = RecordingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        // Offered while it was still fresh, then the sensor stayed out of reach for over an hour.
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 61 * 60_000L, nowMs = now - 30 * 60_000L)).isTrue()
        val answers = mutableListOf<OnePlusCalibrateRx?>()
        val notSent = mutableListOf<OnePlusCalibrationOutcome.NotSent.Reason>()
        val session = OnePlusEgvSession(
            gatt = gatt,
            calibrationQueue = queue,
            onCalibrationResult = { answers += it },
            calibrationSendCheck = allowAll,
            onCalibrationNotSent = { notSent += it },
        )
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        runOneTurn(session)

        assertThat(calibrateFrames(gatt)).isEmpty()
        assertThat(notSent).containsExactly(OnePlusCalibrationOutcome.NotSent.Reason.TOO_OLD)
        assertThat(answers).isEmpty()
    }

    @Test
    fun `a session given a queue but no check writes nothing`() {
        val gatt = RecordingGatt()
        val queue = OnePlusCalibrationQueue()
        val now = System.currentTimeMillis()
        assertThat(queue.offer(glucoseMgdl = 150, bloodAtMs = now - 60_000L, nowMs = now)).isTrue()
        val session = OnePlusEgvSession(gatt = gatt, calibrationQueue = queue)
        setDexClock(session, seconds = 10_000, atMs = now - 30_000L)

        runOneTurn(session)

        assertThat(calibrateFrames(gatt)).isEmpty()
    }

    /** Every check passes: for the tests about the write itself. */
    private val allowAll: (Int, Long, Long) -> OnePlusCalibrationGate.Refusal? = { _, _, _ -> null }

    private fun boundsPacket(glucose: Int, timeSeconds: Int): ByteArray = ByteArray(OnePlusCalibrationBoundsRx.PACKET_LENGTH).also {
        it[0] = OnePlusCalibrationBoundsRx.OPCODE
        it[7] = glucose.toByte()
        it[8] = (glucose shr 8).toByte()
        it[9] = timeSeconds.toByte()
        it[10] = (timeSeconds shr 8).toByte()
        it[11] = (timeSeconds shr 16).toByte()
        it[12] = (timeSeconds shr 24).toByte()
        it[13] = OnePlusCalibrationBoundsRx.Processing.IN_PROGRESS.code.toByte()
        it[14] = 1
    }

    /** No answer to the calibration itself; the calibration state request gets [statePacket]. */
    private class ScriptedGatt(private val statePacket: ByteArray) : OnePlusGattClient by IdleGatt() {
        val writes = mutableListOf<ByteArray>()
        private var next: ByteArray? = null

        override fun isConnected(): Boolean = true

        override fun writeControl(payload: ByteArray?) {
            if (payload == null) return
            writes.add(payload.copyOf())
            if (payload.isNotEmpty() && payload[0] == OnePlusCalibrationBoundsRx.OPCODE) next = statePacket
        }

        override fun enableControlIndications() = Unit

        override fun enableControlNotifications() = Unit

        override fun awaitControlNotify(timeoutMs: Long): ByteArray? = next.also { next = null }
    }

    private fun runTurns(session: OnePlusEgvSession, calls: Int) {
        var left = calls
        session.run(shouldContinue = { left-- > 0 }, notifyTimeoutMs = 1L, rewriteIntervalMs = Long.MAX_VALUE)
    }

    private fun runOneTurn(session: OnePlusEgvSession) {
        var entered = false
        session.run(
            shouldContinue = {
                if (!entered) {
                    entered = true
                    true
                } else {
                    false
                }
            },
            notifyTimeoutMs = 1L,
            rewriteIntervalMs = Long.MAX_VALUE,
        )
    }

    private fun calibrateFrames(gatt: RecordingGatt): List<ByteArray> =
        gatt.writes.filter { it.isNotEmpty() && it[0] == OnePlusCalibrateTx.OPCODE }

    private fun setDexClock(session: OnePlusEgvSession, seconds: Int, atMs: Long) {
        val secondsField = OnePlusEgvSession::class.java.getDeclaredField("lastDexTimeSeconds")
        secondsField.isAccessible = true
        secondsField.setInt(session, seconds)
        val atField = OnePlusEgvSession::class.java.getDeclaredField("lastDexTimeAtMs")
        atField.isAccessible = true
        atField.setLong(session, atMs)
    }

    private class RecordingGatt : OnePlusGattClient by IdleGatt() {
        val writes = mutableListOf<ByteArray>()

        override fun isConnected(): Boolean = true

        override fun writeControl(payload: ByteArray?) {
            if (payload != null) writes.add(payload.copyOf())
        }

        override fun enableControlIndications() = Unit

        override fun enableControlNotifications() = Unit

        override fun awaitControlNotify(timeoutMs: Long): ByteArray? = null
    }

    private class ThrowingGatt : OnePlusGattClient by IdleGatt() {
        val writes = mutableListOf<ByteArray>()

        override fun isConnected(): Boolean = true

        override fun writeControl(payload: ByteArray?) {
            if (payload != null && payload.isNotEmpty() && payload[0] == OnePlusCalibrateTx.OPCODE) {
                throw IllegalStateException("control busy")
            }
        }

        override fun enableControlIndications() = Unit

        override fun enableControlNotifications() = Unit

        override fun awaitControlNotify(timeoutMs: Long): ByteArray? = null
    }

    /** A link that does nothing and receives nothing. Each fake above replaces only what it needs. */
    private class IdleGatt : OnePlusGattClient {
        override fun connect(deviceAddress: String, autoConnect: Boolean) = error("not used in this test")
        override fun disconnect() = Unit
        override fun isConnected(): Boolean = false
        override fun writeAuthentication(payload: ByteArray?) = Unit
        override fun writeExtraData(payload: ByteArray?) = Unit
        override fun writeControl(payload: ByteArray?) = Unit
        override fun enableControlNotifications() = Unit
        override fun enableControlIndications() = Unit
        override fun enableBackfillNotifications() = Unit
        override fun isBonded(): Boolean = false
        override fun createBond(): Boolean = false
        override fun removeBond(): Boolean = false
        override fun awaitBondComplete(timeoutMs: Long): Boolean = false
        override fun awaitKeksNotify(timeoutMs: Long): OnePlusKeksNotify? = null
        override fun awaitNotify(timeoutMs: Long): ByteArray? = null
        override fun awaitControlNotify(timeoutMs: Long): ByteArray? = null
        override fun awaitBackfillNotify(timeoutMs: Long): ByteArray? = null
    }
}
