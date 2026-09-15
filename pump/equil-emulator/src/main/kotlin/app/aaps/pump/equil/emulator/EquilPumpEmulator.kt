package app.aaps.pump.equil.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.Utils
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.security.MessageDigest

/**
 * Emulates the Equil pump's command processing.
 *
 * Handles the 4-phase encrypted protocol:
 * 1. App sends initial request (encrypted with device password)
 * 2. Pump responds with session key (runPwd) + session code (runCode)
 * 3. App sends command data (encrypted with session key)
 * 4. Pump processes command, responds with result
 * 5. App sends confirmation
 * 6. Pump sends final ack
 *
 * Supports all commands used by AAPS:
 * - Running mode get/set (0x02,0x00 / 0x01,0x00)
 * - Temp basal get/set (0x02,0x04 / 0x01,0x04)
 * - Bolus set (0x01,0x03 — CmdLargeBasalSet)
 * - Extended bolus set (0x01,0x03 — CmdExtendedBolusSet, different param layout)
 * - Basal profile set (0x01,0x02)
 * - Insulin get (0x02,0x07)
 * - History get (0x02,0x01)
 * - Alarm set (0x01,0x0b)
 * - Setting set (0x01,0x05)
 * - Time set (0x01,0x00 — shares type with model set, distinguished by port)
 * - Pair (0x0D,0x0D port — special key exchange)
 */
class EquilPumpEmulator(val state: EquilPumpState = EquilPumpState(), private val aapsLogger: AAPSLogger? = null) {

    private var phase = Phase.AWAITING_INITIAL
    private var reqIndex = 10

    /** Extracted from the incoming port during pairing to use as encryption key. */
    /** Last command response data, re-sent in confirmation ack for decodeConfirmData parsing */
    private var lastCommandResponse: ByteArray? = null
    private var pairingRandomPassword: ByteArray? = null

    /** The passwordKey sent to app during pairing, becomes runPwd for phase 2. */
    private var pairingRunPwd: ByteArray? = null

    enum class Phase {
        AWAITING_INITIAL,
        AWAITING_COMMAND,
        AWAITING_CONFIRM,
        PAIRING_AWAITING_CONFIRM
    }

    /**
     * Process an incoming encrypted message.
     *
     * For pairing (port 0D0D), call [processPairInitial] instead.
     */
    fun processMessage(model: EquilCmdModel, password: ByteArray): EquilCmdModel? {
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: processMessage phase=$phase")
        return when (phase) {
            Phase.AWAITING_INITIAL          -> handleInitialRequest(model, password)
            Phase.AWAITING_COMMAND          -> handleCommandData(model, password)
            Phase.AWAITING_CONFIRM          -> handleConfirmation(model, password)
            Phase.PAIRING_AWAITING_CONFIRM  -> handlePairConfirm(model)
        }
    }

    /**
     * Process a pairing initial message (CmdPair/CmdUnPair, port 0D0D).
     *
     * The app sends [equilPassword(32) + randomPassword(32)] encrypted with SHA-256(sn).
     * The pump responds with [deviceKey(32) + passwordKey(32)] encrypted with randomPassword.
     *
     * @param model the encrypted model
     * @param serialNumberHex the serial number in hex (already converted with convertString)
     */
    fun processPairInitial(model: EquilCmdModel, serialNumberHex: String): EquilCmdModel {
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: processPairInitial sn='${state.serialNumber}'")
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Utils.hexStringToBytes(serialNumberHex))
        val snKey = digest.digest()

        // Decrypt to extract randomPassword
        val contentHex: String
        try {
            contentHex = AESUtil.decrypt(model, snKey)
        } catch (e: Exception) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: processPairInitial decrypt failed (corrupted message?), resetting to AWAITING_INITIAL: ${e.message}")
            phase = Phase.AWAITING_INITIAL
            throw e // Let transport catch it and return null
        }
        val contentBytes = Utils.hexStringToBytes(contentHex)
        // Content: [equilPassword(32) + randomPassword(32)]
        val randomPwd = contentBytes.copyOfRange(32, 64)
        pairingRandomPassword = randomPwd

        // Response: [deviceKey(32) + passwordKey(32)] encrypted with randomPassword
        val deviceKey = Utils.hexStringToBytes(state.pairingDeviceKey)
        val passwordKey = Utils.hexStringToBytes(state.pairingPasswordKey)
        pairingRunPwd = passwordKey
        val responseData = Utils.concat(deviceKey, passwordKey)

        phase = Phase.PAIRING_AWAITING_CONFIRM
        return AESUtil.aesEncrypt(randomPwd, responseData)
    }

    /** Get the port string for pairing response. */
    fun getPairResponsePort(): String = "0E0E" + state.sessionCode

    fun getCurrentPassword(): ByteArray = when (phase) {
        Phase.AWAITING_INITIAL          -> state.devicePassword
        Phase.AWAITING_COMMAND          -> state.sessionPassword
        Phase.AWAITING_CONFIRM          -> state.sessionPassword
        Phase.PAIRING_AWAITING_CONFIRM  -> pairingRunPwd ?: state.sessionPassword
    }

    fun getResponsePort(): String = when (phase) {
        Phase.AWAITING_INITIAL          -> "0F0F" + state.sessionCode
        Phase.PAIRING_AWAITING_CONFIRM  -> "0E0E" + state.sessionCode
        else                            -> "0404" + state.sessionCode
    }

    private fun handleInitialRequest(model: EquilCmdModel, password: ByteArray): EquilCmdModel? {
        try {
            AESUtil.decrypt(model, password)
        } catch (e: Exception) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: ignoring stale initial request (decrypt failed: ${e.message})")
            return null
        }

        val indexBytes = Utils.intToBytes(reqIndex)
        val responseData = Utils.concat(indexBytes, state.sessionPassword)
        reqIndex++

        phase = Phase.AWAITING_COMMAND
        lastCommandResponse = null // Clear between sessions
        return AESUtil.aesEncrypt(password, responseData)
    }

    private fun handleCommandData(model: EquilCmdModel, password: ByteArray): EquilCmdModel? {
        val contentHex: String
        try {
            contentHex = AESUtil.decrypt(model, password)
        } catch (e: Exception) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: ignoring stale command (decrypt failed: ${e.message})")
            return null
        }
        val contentBytes = Utils.hexStringToBytes(contentHex)
        val cmdType = if (contentBytes.size >= 6) "%02X,%02X".format(contentBytes[4], contentBytes[5]) else "??"
        val responseData = processCommand(contentBytes)
        lastCommandResponse = responseData
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: handleCommandData cmd=$cmdType responseSize=${responseData.size} response=${Utils.bytesToHex(responseData)}")

        phase = Phase.AWAITING_CONFIRM
        return AESUtil.aesEncrypt(password, responseData)
    }

    private fun handleConfirmation(model: EquilCmdModel, password: ByteArray): EquilCmdModel? {
        try {
            AESUtil.decrypt(model, password)
        } catch (e: Exception) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: stale confirmation (decrypt failed), resetting to AWAITING_INITIAL")
            phase = Phase.AWAITING_INITIAL
            return null
        }

        // Re-send last command response — decodeConfirmData() parses the actual data from it.
        val ackData = lastCommandResponse ?: Utils.concat(Utils.intToBytes(reqIndex), ByteArray(20))
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: confirm ack ${ackData.size} bytes, fromLastCmd=${lastCommandResponse != null}, phase→AWAITING_COMMAND")
        reqIndex++

        phase = Phase.AWAITING_COMMAND
        return AESUtil.aesEncrypt(password, ackData)
    }

    /**
     * Handle phase 2 of pairing: app sends [deviceKey + randomPassword] encrypted with passwordKey.
     * CmdPair.decodeConfirm() just sets cmdSuccess, so we return null (no response needed).
     * CmdUnPair.decodeConfirm() also just sets cmdSuccess.
     */
    private fun handlePairConfirm(model: EquilCmdModel): EquilCmdModel? {
        val runPwd = pairingRunPwd ?: return null // Already processed (stale duplicate)

        try {
            AESUtil.decrypt(model, runPwd)
        } catch (e: Exception) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: ignoring stale pair confirm (decrypt failed: ${e.message})")
            return null
        }

        // After pairing, the app stores pairingPasswordKey in preferences
        // and uses it as the encryption key for all subsequent commands.
        state.devicePassword = Utils.hexStringToBytes(state.pairingPasswordKey)

        // Must send a response so CmdPair.decodeEquilPacket() calls decodeConfirm()
        // which sets cmdSuccess=true. Without a response, the command times out.
        val ackData = Utils.concat(Utils.intToBytes(reqIndex), byteArrayOf(0x00))
        reqIndex++
        val response = AESUtil.aesEncrypt(runPwd, ackData)

        pairingRandomPassword = null
        pairingRunPwd = null
        phase = Phase.AWAITING_INITIAL
        return response
    }

    /** Reset phase to AWAITING_INITIAL without clearing session state.
     *  Used when a new initial request (port 0F0F) arrives mid-session. */
    fun resetToInitial() {
        if (phase != Phase.AWAITING_INITIAL) {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: resetToInitial from $phase")
            phase = Phase.AWAITING_INITIAL
            lastCommandResponse = null
        }
    }

    fun reset() {
        phase = Phase.AWAITING_INITIAL
        reqIndex = 10
        lastCommandResponse = null
        pairingRandomPassword = null
        pairingRunPwd = null
    }

    /**
     * Dispatch command by type bytes.
     * Data format: [4-byte index][cmdType1][cmdType2][params...]
     */
    private fun processCommand(data: ByteArray): ByteArray {
        if (data.size < 6) return indexedResponse()

        val cmdType1 = data[4].toInt() and 0xFF
        val cmdType2 = data[5].toInt() and 0xFF

        return when {
            // --- GET commands (0x02, ...) ---
            cmdType1 == 0x02 && cmdType2 == 0x00 -> processRunningModeGet()
            cmdType1 == 0x02 && cmdType2 == 0x04 -> processTempBasalGet()
            cmdType1 == 0x02 && cmdType2 == 0x07 -> processInsulinGet()
            cmdType1 == 0x02 && cmdType2 == 0x02 -> processResistanceGet()
            cmdType1 == 0x02 && cmdType2 == 0x01 -> processHistoryGet(data)

            // --- SET commands (0x01, ...) ---
            cmdType1 == 0x01 && cmdType2 == 0x04 -> processTempBasalSet(data)
            cmdType1 == 0x01 && cmdType2 == 0x03 -> processBolusOrExtended(data)
            cmdType1 == 0x01 && cmdType2 == 0x02 -> processBasalSet(data)
            cmdType1 == 0x01 && cmdType2 == 0x00 -> processModelOrTimeSet(data)
            cmdType1 == 0x01 && cmdType2 == 0x0b -> processAlarmSet(data)
            cmdType1 == 0x01 && cmdType2 == 0x05 -> processSettingSet(data)

            else                                 -> indexedResponse()
        }
    }

    // --- GET handlers ---

    private fun processRunningModeGet(): ByteArray {
        // Return 24 bytes so both CmdRunningModeGet (reads data[6]) and
        // CmdDevicesGet (reads data[6-7] + data[18-19] firmware) work.
        val response = ByteArray(24)
        putIndex(response)
        response[6] = state.runningMode.toByte()
        // Firmware version at bytes [18-19] for CmdDevicesGet
        val fwMajor = state.firmwareVersion.toInt()
        val fwMinor = ((state.firmwareVersion - fwMajor) * 10).toInt()
        response[18] = fwMajor.toByte()
        response[19] = fwMinor.toByte()
        return response
    }

    private fun processTempBasalGet(): ByteArray {
        val response = ByteArray(14)
        putIndex(response)
        putInt(response, 6, state.tempBasalStep)
        putInt(response, 10, state.tempBasalDuration)
        return response
    }

    private fun processInsulinGet(): ByteArray {
        // CmdInsulinGet.decodeConfirmData reads data[6] as insulin byte
        val response = ByteArray(10)
        putIndex(response)
        response[6] = state.currentInsulin.toByte()
        return response
    }

    private fun processResistanceGet(): ByteArray {
        // CmdResistanceGet.decodeConfirmData reads Utils.bytesToInt(data[7], data[6])
        // enacted = value >= 500 (piston reached insulin)
        val response = ByteArray(8)
        putIndex(response)
        val resistance = state.resistance
        response[6] = (resistance and 0xFF).toByte()
        response[7] = ((resistance shr 8) and 0xFF).toByte()
        return response
    }

    private fun processHistoryGet(data: ByteArray): ByteArray {
        // CmdHistoryGet.decodeConfirmData format (24 bytes total):
        // [4:index][5:padding][6:year][7:month][8:day][9:hour][10:min][11:sec]
        // [12:battery][13:insulin][14-15:rate][16-17:largeRate][18-19:histIndex]
        // [20:padding][21:type][22:level][23:parm]
        val response = ByteArray(24)
        putIndex(response)

        // TODO: use requestedIndex to return matching history event for proper navigation
        val event = if (state.historyEvents.isNotEmpty()) {
            state.historyEvents.lastOrNull() ?: HistoryEvent.now(state.historyIndex, 0)
        } else {
            HistoryEvent.now(state.historyIndex, 0)
        }

        response[6] = event.year.toByte()
        response[7] = event.month.toByte()
        response[8] = event.day.toByte()
        response[9] = event.hour.toByte()
        response[10] = event.minute.toByte()
        response[11] = event.second.toByte()
        response[12] = event.battery.toByte()
        response[13] = event.insulin.toByte()
        // rate: 2 bytes big-endian (Utils.bytesToInt reads [high, low])
        response[14] = (event.rate and 0xFF).toByte()
        response[15] = ((event.rate shr 8) and 0xFF).toByte()
        // largeRate: 2 bytes
        response[16] = (event.largeRate and 0xFF).toByte()
        response[17] = ((event.largeRate shr 8) and 0xFF).toByte()
        // index: 2 bytes big-endian
        response[18] = (event.index and 0xFF).toByte()
        response[19] = ((event.index shr 8) and 0xFF).toByte()
        response[20] = 0x00 // port padding
        response[21] = event.type.toByte()
        response[22] = event.level.toByte()
        response[23] = event.parm.toByte()
        return response
    }

    // --- SET handlers ---

    private fun processTempBasalSet(data: ByteArray): ByteArray {
        if (data.size >= 14) {
            state.tempBasalStep = getInt(data, 6)
            state.tempBasalDuration = getInt(data, 10)
            state.isTempBasalRunning = state.tempBasalStep > 0
            state.tempBasalStartTime = System.currentTimeMillis()
        }
        return indexedResponse()
    }

    /**
     * Both CmdLargeBasalSet and CmdExtendedBolusSet use type 0x01,0x03.
     * Layout differs:
     * - LargeBasal:   [index(4)][0x01,0x03][step(4)][stepTime(4)][0(4)][0(4)]
     * - ExtendedBolus: [index(4)][0x01,0x03][0(4)][0(4)][step(4)][time(4)]
     *
     * Distinguish by checking which pair of int fields is non-zero.
     */
    private fun processBolusOrExtended(data: ByteArray): ByteArray {
        if (data.size >= 22) {
            val field1 = getInt(data, 6)   // LargeBasal: step,        ExtendedBolus: 0
            val field2 = getInt(data, 10)  // LargeBasal: stepTime,    ExtendedBolus: 0
            val field3 = getInt(data, 14)  // LargeBasal: 0,           ExtendedBolus: step
            val field4 = getInt(data, 18)  // LargeBasal: 0,           ExtendedBolus: time

            if (field1 != 0 || field2 != 0) {
                // CmdLargeBasalSet (bolus)
                state.lastBolusStep = field1
                state.lastBolusStepTime = field2
                state.lastBolusTime = System.currentTimeMillis()
            } else {
                // CmdExtendedBolusSet
                state.extendedBolusStep = field3
                state.extendedBolusDuration = field4
                state.isExtendedBolusRunning = field3 > 0
            }
        }
        return indexedResponse()
    }

    private fun processBasalSet(data: ByteArray): ByteArray {
        // CmdBasalSet: [index(4)][0x01,0x02][24 * 4 bytes of rate data]
        // Each hour: [low, high, low, high] (2 bytes repeated)
        if (data.size >= 6 + 24 * 4) {
            for (i in 0 until 24) {
                val offset = 6 + i * 4
                val low = data[offset].toInt() and 0xFF
                val high = data[offset + 1].toInt() and 0xFF
                state.basalRates[i] = (high shl 8) or low
            }
        }
        return indexedResponse()
    }

    /**
     * Type 0x01,0x00 is used by both CmdModelSet (port 0x0505) and CmdTimeSet (port 0x0505).
     * Both set different data, but CmdTimeSet sends 6 time bytes after type, CmdModelSet sends mode int.
     * Distinguish by data size: TimeSet has 12 bytes (4+2+6), ModelSet has 11 bytes (4+2+4+1?).
     * Actually both could have varying size. Use lastPort or just process both fields safely.
     */
    private fun processModelOrTimeSet(data: ByteArray): ByteArray {
        if (data.size >= 12) {
            // Could be time set: [index(4)][0x01,0x00][year][month][day][hour][min][sec]
            val possibleYear = data[6].toInt() and 0xFF
            if (possibleYear in 20..50) {
                // Likely a time set (year 2020-2050)
                processTimeSet(data)
            } else {
                // Mode set
                state.runningMode = getInt(data, 6)
            }
        } else if (data.size >= 10) {
            state.runningMode = getInt(data, 6)
        }
        return indexedResponse()
    }

    private fun processTimeSet(data: ByteArray) {
        if (data.size >= 12) {
            val year = (data[6].toInt() and 0xFF) + 2000
            val month = data[7].toInt() and 0xFF
            val day = data[8].toInt() and 0xFF
            val hour = data[9].toInt() and 0xFF
            val minute = data[10].toInt() and 0xFF
            val second = data[11].toInt() and 0xFF
            val ldt = LocalDateTime(year, month, day, hour, minute, second)
            state.pumpTimeMillis = ldt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }
    }

    private fun processAlarmSet(data: ByteArray): ByteArray {
        if (data.size >= 10) {
            state.alarmMode = getInt(data, 6)
        }
        return indexedResponse()
    }

    private fun processSettingSet(data: ByteArray): ByteArray {
        // CmdSettingSet: [index(4)][0x01,0x05][useTime(4)][autoCloseTime(4)]
        //   [lowAlarm(2)][fastBolus(2)][occlusion(2)][insulinUnit(2)]
        //   [basalThreshold(2)][bolusThreshold(2)]
        if (data.size >= 26) {
            val basalOffset = 22
            val bolusOffset = 24
            state.basalThresholdStep = (data[basalOffset].toInt() and 0xFF) or ((data[basalOffset + 1].toInt() and 0xFF) shl 8)
            state.bolusThresholdStep = (data[bolusOffset].toInt() and 0xFF) or ((data[bolusOffset + 1].toInt() and 0xFF) shl 8)
        }
        return indexedResponse()
    }

    // --- Helpers ---

    /** Build an OK response with index + resistance value at [6-7]. 24 bytes to satisfy any decodeConfirmData.
     *  Resistance is included in all responses because CmdResistanceGet reads [6-7] and its command
     *  phase may be skipped due to session phase confusion. Other commands ignore these bytes. */
    private fun indexedResponse(): ByteArray {
        val response = ByteArray(24)
        putIndex(response)
        response[6] = (state.resistance and 0xFF).toByte()
        response[7] = ((state.resistance shr 8) and 0xFF).toByte()
        return response
    }

    private fun putIndex(array: ByteArray) {
        val indexBytes = Utils.intToBytes(reqIndex)
        System.arraycopy(indexBytes, 0, array, 0, 4)
        reqIndex++
    }

    private fun putInt(array: ByteArray, offset: Int, value: Int) {
        val bytes = Utils.intToBytes(value)
        System.arraycopy(bytes, 0, array, offset, 4)
    }

    private fun getInt(data: ByteArray, offset: Int): Int =
        Utils.bytes2Int(byteArrayOf(data[offset], data[offset + 1], data[offset + 2], data[offset + 3]))
}
