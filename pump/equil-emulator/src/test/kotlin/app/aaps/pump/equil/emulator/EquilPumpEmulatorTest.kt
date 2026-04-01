package app.aaps.pump.equil.emulator

import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest

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

    private fun sendCommand(typeBytes: ByteArray, params: ByteArray = ByteArray(0)) =
        emulator.processMessage(
            AESUtil.aesEncrypt(state.sessionPassword, Utils.concat(Utils.intToBytes(11), typeBytes, params)),
            state.sessionPassword
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
