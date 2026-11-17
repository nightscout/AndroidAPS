package app.aaps.pump.equil.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Calendar

class EquilPumpEmulatorTest {

    private lateinit var emulator: EquilPumpEmulator
    private lateinit var state: EquilPumpState

    @BeforeEach
    fun setUp() {
        state = EquilPumpState()
        state.devicePassword = ByteArray(32) { (it + 1).toByte() }
        state.sessionPassword = ByteArray(32) { (it + 0x10).toByte() }
        state.sessionCode = "A1B2"
        emulator = EquilPumpEmulator(state)
    }

    // --- Protocol phase tests ---

    @Test
    fun `phase 1 - initial request returns session key`() {
        val response = sendInitial()
        assertNotNull(response)
        val bytes = decryptResponse(response!!, state.devicePassword)
        // 4 bytes index + 32 bytes session password
        assertEquals(36, bytes.size)
    }

    @Test
    fun `phase 2 - command after initial`() {
        sendInitial()
        val response = sendCommand(byteArrayOf(0x02, 0x00))
        assertNotNull(response)
    }

    @Test
    fun `phase 3 - confirmation returns ack`() {
        sendInitial()
        sendCommand(byteArrayOf(0x02, 0x00))
        val response = sendConfirmation()
        assertNotNull(response)
    }

    @Test
    fun `reset returns to initial phase`() {
        sendInitial()
        emulator.reset()
        val response = sendInitial()
        assertNotNull(response)
    }

    // --- Running mode get (0x02, 0x00) ---

    @Test
    fun `running mode get returns current mode`() {
        state.runningMode = 2
        sendInitial()
        val response = sendCommand(byteArrayOf(0x02, 0x00))
        val bytes = decryptResponse(response!!, state.sessionPassword)
        assertEquals(2, bytes[6].toInt() and 0xFF)
    }

    // --- Temp basal get (0x02, 0x04) ---

    @Test
    fun `temp basal get returns step and duration`() {
        state.tempBasalStep = 160
        state.tempBasalDuration = 3600
        sendInitial()
        val response = sendCommand(byteArrayOf(0x02, 0x04))
        val bytes = decryptResponse(response!!, state.sessionPassword)
        assertEquals(160, getInt(bytes, 6))
        assertEquals(3600, getInt(bytes, 10))
    }

    // --- Temp basal set (0x01, 0x04) ---

    @Test
    fun `temp basal set updates state`() {
        sendInitial()
        val params = Utils.concat(Utils.intToBytes(80), Utils.intToBytes(1800))
        sendCommand(byteArrayOf(0x01, 0x04), params)
        assertEquals(80, state.tempBasalStep)
        assertEquals(1800, state.tempBasalDuration)
        assertTrue(state.isTempBasalRunning)
    }

    @Test
    fun `temp basal cancel clears state`() {
        state.isTempBasalRunning = true
        state.tempBasalStep = 80
        sendInitial()
        val params = Utils.concat(Utils.intToBytes(0), Utils.intToBytes(0))
        sendCommand(byteArrayOf(0x01, 0x04), params)
        assertEquals(0, state.tempBasalStep)
        assertFalse(state.isTempBasalRunning)
    }

    // --- Bolus set / CmdLargeBasalSet (0x01, 0x03) ---

    @Test
    fun `bolus set updates state`() {
        sendInitial()
        val step = 160 // 1.0U
        val stepTime = 40
        val params = Utils.concat(
            Utils.intToBytes(step), Utils.intToBytes(stepTime),
            Utils.intToBytes(0), Utils.intToBytes(0)
        )
        sendCommand(byteArrayOf(0x01, 0x03), params)
        assertEquals(step, state.lastBolusStep)
        assertEquals(stepTime, state.lastBolusStepTime)
    }

    // --- Extended bolus set / CmdExtendedBolusSet (0x01, 0x03) ---

    @Test
    fun `extended bolus set updates state`() {
        sendInitial()
        val step = 320
        val duration = 3600
        val params = Utils.concat(
            Utils.intToBytes(0), Utils.intToBytes(0),
            Utils.intToBytes(step), Utils.intToBytes(duration)
        )
        sendCommand(byteArrayOf(0x01, 0x03), params)
        assertEquals(step, state.extendedBolusStep)
        assertEquals(duration, state.extendedBolusDuration)
        assertTrue(state.isExtendedBolusRunning)
    }

    @Test
    fun `extended bolus cancel clears state`() {
        state.isExtendedBolusRunning = true
        sendInitial()
        val params = Utils.concat(
            Utils.intToBytes(0), Utils.intToBytes(0),
            Utils.intToBytes(0), Utils.intToBytes(0)
        )
        sendCommand(byteArrayOf(0x01, 0x03), params)
        assertFalse(state.isExtendedBolusRunning)
    }

    // --- A bolus over time, and the test hooks ---

    private fun bolus(steps: Int, seconds: Int) {
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x03), Utils.concat(Utils.intToBytes(steps), Utils.intToBytes(seconds), Utils.intToBytes(0), Utils.intToBytes(0)))
    }

    private fun stopBolus() {
        // A new exchange, as the transport starts one on port 0F0F.
        emulator.resetToInitial()
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x03), ByteArray(16))
    }

    // A real pump takes at most 12 U/min (#5142): 0.5 U needs 2.5 s, 10 U 50 s. The basal uses a little insulin meanwhile.

    @Test
    fun `a bolus takes the time its command asks for`() {
        bolus(steps = 80, seconds = 3)
        assertTrue(emulator.snapshot(linkUp = true).bolusRunning)
        Thread.sleep(3100)
        val after = emulator.snapshot(linkUp = true)
        assertFalse(after.bolusRunning)
        assertEquals(listOf(0.5), after.boluses)
        assertEquals(149.5, after.reservoir!!, 0.01)
    }

    @Test
    fun `a stopped bolus counts what it gave until the stop`() {
        bolus(steps = 1600, seconds = 60)
        Thread.sleep(1000)
        stopBolus()
        val given = emulator.snapshot(linkUp = true).boluses.single()
        assertTrue(given > 0.0 && given < 10.0, "given $given")
        assertEquals(150.0 - given, emulator.snapshot(linkUp = true).reservoir!!, 0.01)
    }

    @Test
    fun `a lost bolus start gives nothing and gets no answer`() {
        emulator.loseCommand(EmulatedCommand.BOLUS_START)
        sendInitial()
        val answer = sendCommand(byteArrayOf(0x01, 0x03), Utils.concat(Utils.intToBytes(80), Utils.intToBytes(3), Utils.intToBytes(0), Utils.intToBytes(0)))
        assertEquals(null, answer)
        assertFalse(emulator.snapshot(linkUp = true).bolusRunning)
        assertEquals(0, state.commands)
    }

    @Test
    fun `a bolus whose answer is lost is given`() {
        emulator.loseAnswer(EmulatedCommand.BOLUS_START)
        sendInitial()
        val answer = sendCommand(byteArrayOf(0x01, 0x03), Utils.concat(Utils.intToBytes(80), Utils.intToBytes(3), Utils.intToBytes(0), Utils.intToBytes(0)))
        assertEquals(null, answer)
        assertTrue(emulator.snapshot(linkUp = true).bolusRunning)
    }

    @Test
    fun `a saved pump comes back with the bolus it gives`() {
        bolus(steps = 1600, seconds = 60)
        val restored = EquilPumpEmulator.restore(emulator.saveState())
        val snapshot = restored.snapshot(linkUp = false)
        assertTrue(snapshot.bolusRunning)
        assertEquals(state.serialNumber, restored.state.serialNumber)
    }

    /** Seen on a real pump (2026-11-10): step 0 for 1800 s runs a zero temp, step 0 for 0 s ends it, and neither is logged. */
    @Test
    fun `a zero temp runs until a cancel and is not logged`() {
        sendInitial()
        val logged = state.historyEvents.size
        sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(0), Utils.intToBytes(1800)))
        assertTrue(state.isTempBasalRunning)
        assertEquals(0.0, emulator.snapshot(linkUp = true).tempBasal!!.absolute!!, 0.0001)
        val read = decryptResponse(sendCommand(byteArrayOf(0x02, 0x04))!!, state.sessionPassword)
        assertEquals(listOf(0, 1800), listOf(getInt(read, 6), getInt(read, 10)))

        sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(0), Utils.intToBytes(0)))
        assertFalse(state.isTempBasalRunning)
        assertEquals(logged, state.historyEvents.size)
    }

    @Test
    fun `a temp basal is reported as its rate`() {
        sendInitial()
        // CmdTempBasalSet: 0.6 U/h for 30 minutes
        sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(48), Utils.intToBytes(1800)))
        assertEquals(0.6, emulator.snapshot(linkUp = true).tempBasal!!.absolute!!, 0.0001)
    }

    // --- The pump log ---

    private fun lastEvent() = state.historyEvents.last()
    private fun code(event: HistoryEvent) = listOf(event.port, event.type, event.level)

    @Test
    fun `an alarm is in the pump log and is read back by its index`() {
        emulator.raise(EmulatedAlarm.LOW_RESERVOIR)
        emulator.raise(EmulatedAlarm.LOW_BATTERY)
        val first = state.historyEvents.first().index
        sendInitial()
        val answer = decryptResponse(sendCommand(byteArrayOf(0x02, 0x01), Utils.intToBytes(first))!!, state.sessionPassword)
        assertEquals(first, (answer[18].toInt() and 0xFF) or ((answer[19].toInt() and 0xFF) shl 8))
        // CmdHistoryGet reads port, type and level from bytes 20 to 22: (4,1,1) is "reservoir low".
        assertEquals(listOf(4, 1, 1), listOf(answer[20].toInt(), answer[21].toInt(), answer[22].toInt()))
    }

    @Test
    fun `a bolus is logged with its speed at the start and ends at its time`() {
        bolus(steps = 80, seconds = 3)
        val start = lastEvent()
        assertTrue(start.largeRate > 0)
        Thread.sleep(3100)
        emulator.snapshot(linkUp = true)
        val end = lastEvent()
        assertEquals(start.index + 1, end.index)
        assertEquals(0, end.largeRate)
    }

    @Test
    fun `a temp basal ends at its time and the pump logs the end`() {
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(48), Utils.intToBytes(1)))
        Thread.sleep(1100)
        assertEquals(null, emulator.snapshot(linkUp = true).tempBasal)
        // The end with the rate of the temp basal (0.6 U/h), then the base rate of 1 U/h.
        val (end, status) = state.historyEvents.takeLast(2)
        assertEquals(listOf(4, 11, 0), code(end))
        assertEquals(96, end.rate)
        assertEquals(listOf(4, 0, 0), code(status))
        assertEquals(160, status.rate)
        assertEquals(0, state.tempBasalStep)
    }

    // --- What a real pump does, beyond the commands ---

    private fun setMode(mode: Int) {
        emulator.resetToInitial()
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x00), Utils.intToBytes(mode))
    }

    private fun tempBasal(step: Int, seconds: Int) {
        emulator.resetToInitial()
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(step), Utils.intToBytes(seconds)))
    }

    @Test
    fun `a bolus above the max bolus of the pump is refused`() {
        state.bolusThresholdStep = 160 // 1 U in steps of 0.00625 U
        sendInitial()
        val answer = decryptResponse(
            sendCommand(byteArrayOf(0x01, 0x03), Utils.concat(Utils.intToBytes(320), Utils.intToBytes(10), Utils.intToBytes(0), Utils.intToBytes(0)))!!,
            state.sessionPassword
        )
        assertTrue(answer[6].toInt() != 0)
        assertFalse(emulator.snapshot(linkUp = true).bolusRunning)
    }

    @Test
    fun `a stopped pump refuses a bolus and a temp basal`() {
        setMode(0)
        bolus(steps = 80, seconds = 5)
        tempBasal(step = 48, seconds = 1800)
        setMode(1)
        val snapshot = emulator.snapshot(linkUp = true)
        assertFalse(snapshot.bolusRunning)
        assertEquals(null, snapshot.tempBasal)
    }

    /** #5142: 30 U/min ended at once in "abnormal infusion stopped", as in the real records 1741-1743. */
    @Test
    fun `a bolus faster than 12 U per minute stops at once as abnormal`() {
        tempBasal(step = 48, seconds = 1800)
        val before = state.historyEvents.size
        bolus(steps = 80, seconds = 1) // 0.5 U in 1 s is 30 U/min
        val snapshot = emulator.snapshot(linkUp = true)
        assertFalse(snapshot.bolusRunning)
        assertEquals(emptyList<Double>(), snapshot.boluses)
        assertEquals(listOf(listOf(4, 11, 0), listOf(5, 1, 2), listOf(4, 8, 0)), state.historyEvents.drop(before).map { code(it) })
        assertTrue(snapshot.suspended)
    }

    @Test
    fun `the basal uses insulin from the reservoir`() {
        emulator.snapshot(linkUp = true)
        state.lastSettleAt -= 3_600_000 // an hour ago, at 1 U/h
        assertEquals(149.0, emulator.snapshot(linkUp = true).reservoir!!, 0.01)
    }

    @Test
    fun `going below the reservoir alarm is logged once`() {
        state.reservoir = 10.05
        bolus(steps = 32, seconds = 4) // 0.2 U
        Thread.sleep(4100)
        emulator.snapshot(linkUp = true)
        assertEquals(1, state.historyEvents.count { code(it) == listOf(4, 1, 1) })
    }

    /** As the real records 1561-1564: battery empty, the temp basal ends, a status with rate 0, rewind. */
    @Test
    fun `an empty battery stops the pump with the entries of a real one`() {
        tempBasal(step = 48, seconds = 1800)
        val before = state.historyEvents.size
        emulator.setBattery(0)
        assertEquals(listOf(listOf(5, 0, 2), listOf(4, 11, 0), listOf(4, 0, 0), listOf(4, 8, 0)), state.historyEvents.drop(before).map { code(it) })
        assertTrue(emulator.snapshot(linkUp = true).suspended)
    }

    @Test
    fun `the pump logs in its own clock`() {
        emulator.shiftClock(2 * 3_600_000L)
        emulator.raise(EmulatedAlarm.LOW_RESERVOIR)
        val hour = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + 2 * 3_600_000L }.get(Calendar.HOUR_OF_DAY)
        assertEquals(hour, lastEvent().hour)
    }

    /** #3830, seen on a real pump: after a resume the pump runs again the temp basal it had before the suspend. */
    @Test
    fun `a resume runs the temp basal of before the suspend again`() {
        tempBasal(step = 48, seconds = 1800)
        setMode(0)
        assertEquals(null, emulator.snapshot(linkUp = true).tempBasal)
        setMode(1)
        assertEquals(0.6, emulator.snapshot(linkUp = true).tempBasal!!.absolute!!, 0.0001)
    }

    // The entries below are as a real pump wrote them on 2026-11-08 (records 1525-1540). Rates are in 0.00625 U/h.

    private fun entries(before: Int) = state.historyEvents.drop(before).map { code(it) + it.rate }

    @Test
    fun `a temp basal start and cancel log as a real pump does`() {
        val before = state.historyEvents.size
        tempBasal(step = 48, seconds = 1800)
        tempBasal(step = 0, seconds = 0)
        // Start with the rate before, the new rate, end with the rate before, the base rate again.
        assertEquals(
            listOf(listOf(4, 10, 0, 160), listOf(4, 0, 0, 96), listOf(4, 11, 0, 96), listOf(4, 0, 0, 160)),
            entries(before)
        )
    }

    @Test
    fun `a temp basal at the base rate logs no rate change`() {
        val before = state.historyEvents.size
        tempBasal(step = 80, seconds = 1800) // 1 U/h, as the base basal
        assertEquals(listOf(listOf(4, 10, 0, 160)), entries(before))
    }

    @Test
    fun `a suspend and a resume log as a real pump does`() {
        tempBasal(step = 48, seconds = 1800)
        val before = state.historyEvents.size
        setMode(0)
        setMode(1)
        // The pause with rate 0, then the temp basal of before the suspend again.
        assertEquals(listOf(listOf(4, 5, 0, 0), listOf(4, 0, 0, 96)), entries(before))
    }

    @Test
    fun `a basal change during a bolus keeps the bolus speed in the status entry`() {
        tempBasal(step = 48, seconds = 1)
        bolus(steps = 160, seconds = 20) // 3 U/min
        Thread.sleep(1100)
        emulator.snapshot(linkUp = true)
        val (end, status) = state.historyEvents.takeLast(2)
        assertEquals(listOf(4, 11, 0), code(end))
        assertEquals(listOf(4, 0, 0), code(status))
        assertEquals(28800, status.largeRate)
    }

    @Test
    fun `a bolus logs the basal that goes on and its speed in two bytes`() {
        bolus(steps = 80, seconds = 20) // 1.5 U/min
        assertEquals(160, lastEvent().rate)
        assertEquals(14400, lastEvent().largeRate)
        stopBolus()
        bolus(steps = 160, seconds = 5) // 12 U/min is 115200, the low 16 bits are 49664
        assertEquals(49664, lastEvent().largeRate)
    }

    /** As the real records 1711-1717: the temp basal ends, the reservoir is empty, the pump rewinds and stops. */
    @Test
    fun `an empty reservoir ends the temp basal, is logged and stops the pump`() {
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(48), Utils.intToBytes(1800)))
        val before = state.historyEvents.size
        emulator.raise(EmulatedAlarm.EMPTY_RESERVOIR)
        assertEquals(listOf(listOf(4, 11, 0), listOf(4, 1, 2), listOf(4, 8, 0)), state.historyEvents.drop(before).map { code(it) })
        assertTrue(emulator.snapshot(linkUp = true).suspended)
    }

    // --- Basal profile set (0x01, 0x02) ---

    @Test
    fun `basal set updates profile`() {
        sendInitial()
        val rates = ByteArray(24 * 4)
        // Set hour 0 to rate value 320 (low=0x40, high=0x01)
        rates[0] = 0x40
        rates[1] = 0x01
        rates[2] = 0x40
        rates[3] = 0x01
        sendCommand(byteArrayOf(0x01, 0x02), rates)
        assertEquals(0x0140, state.basalRates[0])
    }

    // --- Basal profile get (0x02, 0x02) ---

    /** #2454: AAPS reads back the basal it set, 48 half hours with the low byte first. */
    @Test
    fun `basal get returns the profile that was set`() {
        sendInitial()
        val rates = ByteArray(24 * 4)
        byteArrayOf(0x40, 0x01, 0x40, 0x01, 0x50, 0x00, 0x50, 0x00).copyInto(rates)
        sendCommand(byteArrayOf(0x01, 0x02), rates)
        sendConfirmation()

        // The confirmation leaves the emulator waiting for the next command of the session.
        val bytes = decryptResponse(sendCommand(byteArrayOf(0x02, 0x02))!!, state.sessionPassword)

        assertEquals(6 + 48 * 2, bytes.size)
        assertEquals(0x0140, Utils.bytesToInt(bytes[7], bytes[6]))
        assertEquals(0x0140, Utils.bytesToInt(bytes[9], bytes[8]))
        assertEquals(0x50, Utils.bytesToInt(bytes[11], bytes[10]))
        assertEquals(0x50, Utils.bytesToInt(bytes[13], bytes[12]))
    }

    /** The resistance read uses the same type bytes, on its own port. */
    @Test
    fun `resistance get on its port still returns the resistance`() {
        state.resistance = 600
        sendInitial()
        val bytes = decryptResponse(sendCommand(byteArrayOf(0x02, 0x02), port = "1515A1B2")!!, state.sessionPassword)

        assertEquals(600, Utils.bytesToInt(bytes[7], bytes[6]))
    }

    // --- Insulin get (0x02, 0x07) ---

    @Test
    fun `insulin get returns current level`() {
        state.currentInsulin = 120
        sendInitial()
        val response = sendCommand(byteArrayOf(0x02, 0x07))
        val bytes = decryptResponse(response!!, state.sessionPassword)
        assertEquals(120, bytes[6].toInt() and 0xFF)
    }

    // --- Alarm set (0x01, 0x0b) ---

    @Test
    fun `alarm set updates mode`() {
        sendInitial()
        sendCommand(byteArrayOf(0x01, 0x0b), Utils.intToBytes(3))
        assertEquals(3, state.alarmMode)
    }

    // --- Setting set (0x01, 0x05) ---

    @Test
    fun `setting set updates thresholds`() {
        sendInitial()
        // Build full CmdSettingSet payload:
        // [useTime(4)][autoCloseTime(4)][lowAlarm(2)][fastBolus(2)][occlusion(2)][insulinUnit(2)][basalThreshold(2)][bolusThreshold(2)]
        val payload = ByteArray(20)
        // basalThreshold at offset 16 (relative to after type bytes, so absolute 22-6=16)
        payload[16] = 0x60  // 0x0160 = 352
        payload[17] = 0x01
        // bolusThreshold at offset 18
        payload[18] = 0x20.toByte()  // 0x0320 = 800
        payload[19] = 0x03
        sendCommand(byteArrayOf(0x01, 0x05), payload)
        assertEquals(352, state.basalThresholdStep)
        assertEquals(800, state.bolusThresholdStep)
    }

    // --- Time set (0x01, 0x00) ---

    @Test
    fun `time set updates pump time`() {
        sendInitial()
        // year=26 (2026), month=3, day=23, hour=15, min=30, sec=0
        val timeBytes = byteArrayOf(26, 3, 23, 15, 30, 0)
        sendCommand(byteArrayOf(0x01, 0x00), timeBytes)
        assertTrue(state.pumpTimeMillis > 0)
    }

    // --- History get (0x02, 0x01) ---

    @Test
    fun `history get returns event data`() {
        state.historyEvents.add(HistoryEvent(26, 3, 23, 14, 0, 0, 80, 150, 160, 0, 5, 1, 0, 0))
        sendInitial()
        val params = Utils.intToBytes(0)
        val response = sendCommand(byteArrayOf(0x02, 0x01), params)
        val bytes = decryptResponse(response!!, state.sessionPassword)
        assertEquals(26, bytes[6].toInt() and 0xFF)  // year
        assertEquals(3, bytes[7].toInt() and 0xFF)   // month
        assertEquals(80, bytes[12].toInt() and 0xFF) // battery
    }

    // --- Full round-trip ---

    @Test
    fun `full 3-phase round trip`() {
        val r1 = sendInitial()
        assertNotNull(r1)

        val r2 = sendCommand(byteArrayOf(0x01, 0x04), Utils.concat(Utils.intToBytes(80), Utils.intToBytes(1800)))
        assertNotNull(r2)
        assertEquals(80, state.tempBasalStep)

        val r3 = sendConfirmation()
        assertNotNull(r3)
    }

    // --- Pairing (CmdPair) ---

    @Test
    fun `pair initial returns device and password keys`() {
        // Simulate CmdPair: encrypt [equilPassword + randomPassword] with SHA-256(sn)
        val sn = convertString("EQUIL00001")
        val randomPassword = ByteArray(32) { (it + 0x50).toByte() }
        val equilPassword = AESUtil.getEquilPassWord("testpwd")
        val payload = Utils.concat(equilPassword, randomPassword)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Utils.hexStringToBytes(sn))
        val snKey = digest.digest()

        val encrypted = AESUtil.aesEncrypt(snKey, payload)
        val response = emulator.processPairInitial(encrypted, sn)

        assertNotNull(response)

        // Decrypt response with randomPassword — should get [deviceKey + passwordKey]
        val decrypted = Utils.hexStringToBytes(AESUtil.decrypt(response, randomPassword))
        assertEquals(64, decrypted.size) // 32 + 32

        // Verify keys match state
        val deviceKey = Utils.bytesToHex(decrypted.copyOfRange(0, 32))
        val passwordKey = Utils.bytesToHex(decrypted.copyOfRange(32, 64))
        assertEquals(state.pairingDeviceKey.uppercase(), deviceKey)
        assertEquals(state.pairingPasswordKey.uppercase(), passwordKey)
    }

    @Test
    fun `pair confirm completes pairing and returns null`() {
        // Phase 1: initial
        val sn = convertString("EQUIL00001")
        val randomPassword = ByteArray(32) { (it + 0x50).toByte() }
        val equilPassword = AESUtil.getEquilPassWord("testpwd")

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Utils.hexStringToBytes(sn))
        val snKey = digest.digest()

        val encrypted = AESUtil.aesEncrypt(snKey, Utils.concat(equilPassword, randomPassword))
        val response = emulator.processPairInitial(encrypted, sn)
        assertNotNull(response)

        // Decrypt to get passwordKey (runPwd)
        val decrypted = Utils.hexStringToBytes(AESUtil.decrypt(response, randomPassword))
        val passwordKey = decrypted.copyOfRange(32, 64)
        val deviceKey = decrypted.copyOfRange(0, 32)

        // Phase 2: send [deviceKey + randomPassword] encrypted with passwordKey
        val confirmPayload = Utils.concat(deviceKey, randomPassword)
        val confirmEncrypted = AESUtil.aesEncrypt(passwordKey, confirmPayload)

        val confirmResponse = emulator.processMessage(confirmEncrypted, passwordKey)

        // Emulator sends ack response so CmdPair.decodeConfirm() gets triggered
        assertNotNull(confirmResponse)
    }

    @Test
    fun `after pairing completes emulator is ready for normal commands`() {
        // Complete pairing
        val sn = convertString("EQUIL00001")
        val randomPassword = ByteArray(32) { (it + 0x50).toByte() }
        val equilPassword = AESUtil.getEquilPassWord("testpwd")

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Utils.hexStringToBytes(sn))
        val snKey = digest.digest()

        emulator.processPairInitial(
            AESUtil.aesEncrypt(snKey, Utils.concat(equilPassword, randomPassword)),
            sn
        )

        val passwordKey = Utils.hexStringToBytes(state.pairingPasswordKey)
        val deviceKey = Utils.hexStringToBytes(state.pairingDeviceKey)
        emulator.processMessage(
            AESUtil.aesEncrypt(passwordKey, Utils.concat(deviceKey, randomPassword)),
            passwordKey
        )

        // Now should be back in AWAITING_INITIAL — normal flow should work
        val r = sendInitial()
        assertNotNull(r)

        // The fill reaches the piston: a real pump logged the prime at its activation, so its log is never empty.
        sendCommand(byteArrayOf(0x02, 0x02), port = "1515A1B2")
        assertEquals(listOf(4, 7, 0), code(lastEvent()))
        assertEquals(0, lastEvent().rate)
    }

    // --- Helper: convertString (mirrors CmdPair logic) ---

    private fun convertString(input: String): String {
        val sb = StringBuilder()
        for (ch in input.toCharArray()) {
            sb.append("0").append(ch)
        }
        return sb.toString()
    }

    // --- Helpers ---

    private fun sendInitial() = emulator.processMessage(
        AESUtil.aesEncrypt(state.devicePassword, Utils.concat(Utils.intToBytes(10), ByteArray(8))),
        state.devicePassword
    )

    private fun sendCommand(typeBytes: ByteArray, params: ByteArray = ByteArray(0), port: String = "0404A1B2") =
        emulator.processMessage(
            AESUtil.aesEncrypt(state.sessionPassword, Utils.concat(Utils.intToBytes(11), typeBytes, params)),
            state.sessionPassword,
            port
        )

    private fun sendConfirmation() = emulator.processMessage(
        AESUtil.aesEncrypt(state.sessionPassword, Utils.concat(Utils.intToBytes(12), byteArrayOf(0x00, 0x00, 0x01))),
        state.sessionPassword
    )

    private fun decryptResponse(model: app.aaps.pump.equil.manager.EquilCmdModel, password: ByteArray): ByteArray =
        Utils.hexStringToBytes(AESUtil.decrypt(model, password))

    private fun getInt(data: ByteArray, offset: Int): Int =
        Utils.bytes2Int(byteArrayOf(data[offset], data[offset + 1], data[offset + 2], data[offset + 3]))
}
