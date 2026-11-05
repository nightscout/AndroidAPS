package app.aaps.pump.aaruy.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * The emulator alone, with a test key and a clock the test moves. These tests fix the emulator's behaviour.
 * They do not prove the real pump does the same: see the UNVERIFIED notes in [AaruyPumpEmulator].
 */
class AaruyPumpEmulatorTest {

    private class Message(val paramId: Int, val payload: ByteArray) {

        fun reader(): ByteBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    }

    private val key = ByteArray(16) { it.toByte() }
    private var now = BASE_TIME_MS + 1_000_000_000L
    private val emulator = AaruyPumpEmulator(keyProvider = { key }, clock = { now })
    private var sequence: Short = 0

    private fun cipher(mode: Int) = Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES")) }

    private fun frame(command: Int, paramId: Int, payload: ByteArray = ByteArray(0), seq: Short = ++sequence): ByteArray {
        val plain = ByteBuffer.allocate(7 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((payload.size + 5).toShort()).put(command.toByte()).putShort(paramId.toShort()).putShort(seq).put(payload).array()
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        return ByteBuffer.allocate(encrypted.size + 3).order(ByteOrder.LITTLE_ENDIAN).put(0x55).putShort(encrypted.size.toShort()).put(encrypted).array()
    }

    private fun decode(frame: ByteArray): Message {
        val plain = cipher(Cipher.DECRYPT_MODE).doFinal(frame.copyOfRange(3, frame.size))
        return Message(ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).getShort(3).toInt(), plain.copyOfRange(7, plain.size))
    }

    private fun write(paramId: Int, payload: ByteArray = ByteArray(0)) = emulator.handle(frame(1, paramId, payload)).map { decode(it) }
    private fun read(paramId: Int) = emulator.handle(frame(0, paramId)).map { decode(it) }
    private fun tick() = emulator.tick().map { decode(it) }

    private fun buffer(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun control(action: Int, extra: Int = 0) = buffer(8).putInt(0).put(action.toByte()).put(extra.toByte()).array()

    private fun bolusSetting(now: Int, delayed: Int = 0, minutes: Int = 0) =
        buffer(16).putInt(0).putShort((now + delayed).toShort()).putShort(0).putShort(0).putShort(0).putShort(now.toShort()).putShort(minutes.toShort()).array()

    private fun tempBasal(action: Int, rate: Int, minutes: Int) =
        buffer(9).putInt(0).put(action.toByte()).putShort(rate.toShort()).putShort(minutes.toShort()).array()

    private fun status(message: Message): Int = message.payload[6].toInt()
    private fun bolusState(message: Message): Int = message.payload[7].toInt()
    private fun bolusNow(message: Message): Int = message.reader().getShort(8).toInt() and 0xFFFF

    private fun startBolus(units: Int): List<Message> {
        write(PARAM_BOLUS_SETTING, bolusSetting(units))
        return write(PARAM_BOLUS_CONTROL, control(1))
    }

    /** Moves time in small steps, like the transport does, and gives all messages sent. */
    private fun advance(ms: Long): List<Message> {
        val out = ArrayList<Message>()
        var left = ms
        while (left > 0) {
            val step = minOf(left, 200L)
            now += step
            left -= step
            out.addAll(tick())
        }
        return out
    }

    @Test
    fun startsOnBasalAndAnswersStatus() {
        val answer = read(PARAM_STATUS).single()
        assertEquals(PARAM_STATUS, answer.paramId)
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(answer))
        assertEquals(200, answer.reader().getShort(4).toInt() and 0xFFFF)
    }

    @Test
    fun answersRealData() {
        emulator.battery = 42
        val answer = read(PARAM_REAL_DATA).single()
        assertEquals(42, answer.payload[0].toInt())
    }

    @Test
    fun reportDeviceIsAcceptedWithProtocolVersion() {
        val answer = write(PARAM_REPORT_DEVICE, buffer(8).putInt(0).put(7).put(1).putShort(0).array()).single()
        assertEquals(PARAM_REPORT_DEVICE, answer.paramId)
        assertEquals(6, answer.payload[4].toInt())
        assertEquals(1, answer.payload[5].toInt())
    }

    @Test
    fun wrongSequenceIsRefusedAndSerialTellsTheRightOne() {
        read(PARAM_STATUS)
        val refused = emulator.handle(frame(0, PARAM_STATUS, seq = 500)).map { decode(it) }.single()
        assertEquals(PARAM_ERROR, refused.paramId)
        assertEquals(28, refused.payload[0].toInt())
        assertEquals(0x80, refused.payload[1].toInt() and 0xFF)
        assertEquals(1, emulator.rejectedFrames)

        val serial = emulator.handle(frame(0, PARAM_SERIAL, seq = 500)).map { decode(it) }.single()
        assertEquals(1, serial.reader().short.toInt())
    }

    @Test
    fun brokenFrameGetsNoAnswer() {
        assertEquals(0, emulator.handle(ByteArray(19) { 7 }).size)
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun basicParamIsStoredAndEchoed() {
        val param = ByteArray(32) { it.toByte() }
        assertTrue(write(PARAM_BASIC, param).single().payload.contentEquals(param))
        assertTrue(read(PARAM_BASIC).single().payload.contentEquals(param))
    }

    @Test
    fun bolusDeliversInStepsAndReturnsToBasal() {
        val started = startBolus(100).single()
        assertEquals(AaruyPumpEmulator.STATUS_BOLUS, status(started))

        // The default bolus step is 50 thousandths, the first at once and then one every 2 seconds.
        val messages = advance(4 * AaruyPumpEmulator.BOLUS_STEP_MS)
        assertEquals(listOf(50, 100), messages.filter { status(it) == AaruyPumpEmulator.STATUS_BOLUS && bolusState(it) == 0 }.map { bolusNow(it) })
        val statusAfter = messages.takeLast(2)
        assertEquals(2, bolusState(statusAfter[0]))
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(statusAfter[1]))
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun bolusLowersTheReservoir() {
        emulator.reservoir = 100.0
        startBolus(1000)
        advance(40 * AaruyPumpEmulator.BOLUS_STEP_MS)
        // 1 U of bolus, and a little basal.
        assertTrue(emulator.reservoir in 98.9..99.0, "reservoir ${emulator.reservoir}")
    }

    /** Seen on a real pump with a step of 0.05 U: 0.125 U is refused with two errors, 0.10 U runs. */
    @Test
    fun aBolusNotOnTheStepIsRefused() {
        val answers = startBolus(125)
        assertEquals(listOf(PARAM_ERROR, PARAM_ERROR), answers.map { it.paramId })
        assertEquals(AaruyPumpEmulator.ALARM_RUN_BOLUS and 0xFF, answers.last().payload[0].toInt() and 0xFF)
        assertEquals(PARAM_STATUS, startBolus(100).single().paramId)
    }

    @Test
    fun bolusWithoutSettingIsRefused() {
        val answer = write(PARAM_BOLUS_CONTROL, control(1)).single()
        assertEquals(PARAM_ERROR, answer.paramId)
        assertEquals(AaruyPumpEmulator.ALARM_RUN_BOLUS and 0xFF, answer.payload[0].toInt() and 0xFF)
    }

    @Test
    fun stoppedBolusGoesBackToBasalAndHistoryHasTheDeliveredAmount() {
        startBolus(1000)
        advance(2 * AaruyPumpEmulator.BOLUS_STEP_MS)
        val stopped = write(PARAM_BOLUS_CONTROL, control(0)).single()
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(stopped))

        val log = write(PARAM_HISTORY, buffer(8).putInt(0).array()).single()
        val entries = log.payload.size / 12
        assertEquals(PARAM_HISTORY_LOG, log.paramId)
        val bolus = (0 until entries).map { 5 + it * 12 }.first { log.payload[it + 4].toInt() == 2 }
        assertEquals(150, log.reader().getShort(bolus + 5).toInt())
    }

    @Test
    fun tempBasalRunsAndEndsByItself() {
        val started = write(PARAM_TEMP_BASAL, tempBasal(1, 1500, 30)).single()
        assertEquals(AaruyPumpEmulator.STATUS_TEMP_BASAL, status(started))
        assertEquals(1500, started.reader().getShort(8).toInt() and 0xFFFF)

        assertEquals(0, advance(29 * 60_000L).count { it.paramId != PARAM_REAL_DATA })
        val ended = advance(2 * 60_000L).single { it.paramId != PARAM_REAL_DATA }
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(ended))
    }

    @Test
    fun tempBasalCanBeCancelled() {
        write(PARAM_TEMP_BASAL, tempBasal(1, 1500, 30))
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(write(PARAM_TEMP_BASAL, tempBasal(0, 0, 0)).single()))
    }

    @Test
    fun tempBasalNeedsBasalRunning() {
        startBolus(1000)
        val answer = write(PARAM_TEMP_BASAL, tempBasal(1, 1500, 30)).single()
        assertEquals(PARAM_ERROR, answer.paramId)
        assertEquals(AaruyPumpEmulator.ALARM_RUN_TEMP_BASAL and 0xFF, answer.payload[0].toInt() and 0xFF)
    }

    @Test
    fun basalDeliveryFollowsTheRate() {
        emulator.reservoir = 100.0
        advance(3_600_000L)
        // The default basal is 0.5 U/h.
        assertEquals(99.5, emulator.reservoir, 0.01)
    }

    @Test
    fun newBasalProfileIsEchoedAndUsed() {
        val profile = buffer(15).putInt(0).put(3).put(0).putShort(0).putShort(240).put(0).put(1)
            .put(0).putShort(1000).array()
        assertTrue(write(PARAM_BASAL, profile).single().payload.contentEquals(profile))
        assertTrue(read(PARAM_BASAL).single().payload.contentEquals(profile))
        assertEquals(1000, read(PARAM_STATUS).single().reader().getShort(8).toInt() and 0xFFFF)
    }

    @Test
    fun replaceGoesThroughRewindToStopped() {
        assertEquals(AaruyPumpEmulator.STATUS_REWIND, status(write(PARAM_TOTAL_CONTROL, control(3)).single()))
        val messages = advance(AaruyPumpEmulator.TRANSITION_MS)
        assertEquals(PARAM_ERROR, messages[0].paramId)
        assertEquals(17, messages[0].payload[0].toInt())
        assertEquals(AaruyPumpEmulator.STATUS_STOPPED, status(messages[1]))

        assertEquals(AaruyPumpEmulator.STATUS_NOT_RUNNING, status(write(PARAM_TOTAL_CONTROL, control(6)).single()))
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(write(PARAM_BASAL_CONTROL, buffer(6).putInt(0).put(1).put(0).array()).single()))
    }

    @Test
    fun positionFillsTheReservoir() {
        emulator.reservoir = 1.0
        assertEquals(AaruyPumpEmulator.STATUS_POSITION, status(write(PARAM_TOTAL_CONTROL, control(5)).single()))
        val messages = advance(AaruyPumpEmulator.TRANSITION_MS)
        assertEquals(AaruyPumpEmulator.STATUS_STOPPED, status(messages[0]))
        assertEquals(22, messages[1].payload[0].toInt())
        assertEquals(299.0, emulator.reservoir, 0.01)
    }

    @Test
    fun pushAirEndsStoppedWithNoEvent() {
        assertEquals(AaruyPumpEmulator.STATUS_DRAIN, status(write(PARAM_TOTAL_CONTROL, control(4, 1)).single()))
        assertEquals(AaruyPumpEmulator.STATUS_STOPPED, status(advance(AaruyPumpEmulator.TRANSITION_MS).single()))
    }

    @Test
    fun timeSyncSendsAllSettingsAsARealPump() {
        val answer = write(PARAM_SYNC_TIME, buffer(4).putInt(1).array())
        assertEquals(listOf(0, 0, 1, 2, 3, 4, 6, 15), answer.map { it.paramId })
    }

    @Test
    fun basalIsLoggedWhenItEndsWithItsRealMinutes() {
        now += 3 * 60_000L
        emulator.tick()
        startBolus(100)
        val log = write(PARAM_HISTORY, buffer(8).putInt(0).array()).single()
        val entry = log.reader()
        assertEquals(1, entry.getShort(0).toInt())
        assertEquals(0, entry.get(5 + 4).toInt())
        assertEquals(3, entry.getShort(5 + 7).toInt())
    }

    @Test
    fun theLastHistoryEntryComesAgainWhenAskedFromItsTime() {
        startBolus(100)
        val time = write(PARAM_HISTORY, buffer(8).putInt(0).array()).single().reader().getInt(5)
        val again = write(PARAM_HISTORY, buffer(8).putInt(time).array()).single()
        assertEquals(1, again.reader().getShort(0).toInt())
    }

    @Test
    fun aStopWithNoBolusAnswersWithTheStatus() {
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, status(write(PARAM_BOLUS_CONTROL, control(0)).single()))
    }

    @Test
    fun batteryAndSignalComeEveryMinute() {
        assertEquals(0, advance(30_000L).size)
        assertEquals(PARAM_REAL_DATA, advance(31_000L).single().paramId)
    }

    @Test
    fun stoppedForLongGivesOneWarning() {
        write(PARAM_TOTAL_CONTROL, control(3))
        advance(AaruyPumpEmulator.TRANSITION_MS)
        val warnings = advance(20 * 60_000L).filter { it.paramId == PARAM_ERROR }
        assertEquals(AaruyPumpEmulator.WARNING_STOPPED_TOO_LONG and 0xFF, warnings.single().payload[0].toInt())
    }

    @Test
    fun newHistoryIsEmptyAsOnARealPump() {
        val log = write(PARAM_HISTORY, buffer(8).putInt(0).array()).single()
        assertEquals(PARAM_HISTORY_LOG, log.paramId)
        assertEquals(listOf<Byte>(0, 0, 0, 0, 0), log.payload.toList())
    }

    @Test
    fun historyAfterTheLastEntryIsEmpty() {
        val log = write(PARAM_HISTORY, buffer(8).putInt(Int.MAX_VALUE).array()).single()
        assertEquals(5, log.payload.size)
    }

    @Test
    fun longHistoryHasPages() {
        repeat(25) { index ->
            emulator.raiseAlarm(0x8105 + index % 2)
            now += 1000
        }
        val pages = write(PARAM_HISTORY, buffer(8).putInt(0).array())
        assertEquals(3, pages.size)
        assertEquals(listOf(0, 1, 2), pages.map { it.payload[3].toInt() })
        assertTrue(pages.all { it.payload[2].toInt() == 3 })
        assertEquals(listOf(10, 10, 5), pages.map { it.payload[4].toInt() })
    }

    @Test
    fun dailyHistoryIsLimitedAndEndsToday() {
        val daily = write(PARAM_HISTORY, buffer(8).putInt(0).put(0).put(1).array()).single()
        assertEquals(PARAM_HISTORY_DAILY, daily.paramId)
        assertEquals(10, daily.payload[0].toInt())
        assertEquals(1 + 10 * 6, daily.payload.size)
    }

    @Test
    fun lowReservoirAlarmsOnce() {
        emulator.reservoir = 19.0
        val first = tick().single()
        assertEquals(PARAM_ERROR, first.paramId)
        assertEquals(AaruyPumpEmulator.ALARM_LOW_RESERVOIR and 0xFF, first.payload[0].toInt() and 0xFF)
        assertEquals(0, tick().size)
    }

    @Test
    fun emptyReservoirStopsDelivery() {
        emulator.reservoir = 0.0
        val messages = tick()
        assertTrue(messages.any { it.paramId == PARAM_ERROR && (it.payload[0].toInt() and 0xFF) == (AaruyPumpEmulator.ALARM_NO_RESERVOIR and 0xFF) })
        assertEquals(AaruyPumpEmulator.STATUS_PAUSED, emulator.runningStatus)
    }

    @Test
    fun closedModeGetsNoAnswerAsOnARealPump() {
        assertEquals(0, write(PARAM_CLOSED_MODE, byteArrayOf(1)).size)
        assertEquals(1, emulator.closedMode)
    }

    private companion object {

        const val BASE_TIME_MS = 1609430400000L
        const val PARAM_BASIC = 0
        const val PARAM_BASAL = 1
        const val PARAM_BOLUS_SETTING = 4
        const val PARAM_SYNC_TIME = 5
        const val PARAM_STATUS = 6
        const val PARAM_BASAL_CONTROL = 7
        const val PARAM_TEMP_BASAL = 8
        const val PARAM_BOLUS_CONTROL = 9
        const val PARAM_TOTAL_CONTROL = 10
        const val PARAM_ERROR = 12
        const val PARAM_HISTORY = 13
        const val PARAM_HISTORY_LOG = 14
        const val PARAM_REAL_DATA = 15
        const val PARAM_REPORT_DEVICE = 17
        const val PARAM_HISTORY_DAILY = 20
        const val PARAM_SERIAL = 21
        const val PARAM_CLOSED_MODE = 24
    }
}
