package app.aaps.pump.equil.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedExtendedBolus
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilCmdModel
import app.aaps.pump.equil.manager.Utils
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest
import java.util.Calendar
import kotlin.math.roundToInt

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
 * - Basal profile get (0x02,0x02) and resistance get (0x02,0x02 on port 1515)
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

    /** Test hooks: commands that never reach the pump, and commands whose answer is lost. */
    private val lostCommands = mutableListOf<EmulatedCommand>()
    private val lostAnswers = mutableListOf<EmulatedCommand>()

    enum class Phase {
        AWAITING_INITIAL,
        AWAITING_COMMAND,
        AWAITING_CONFIRM,
        PAIRING_AWAITING_CONFIRM
    }

    /**
     * True when the last response was the ack of a confirmation. It repeats the answer it confirms, and the transport
     * sends it with the same packet index: the app drops a packet with the index it has just handled, and that ends the
     * exchange. With a new index the app confirmed again, the emulator answered again, and a read sent next on the
     * same link got a stale answer.
     */
    var lastResponseWasConfirmationAck = false
        private set

    /**
     * Process an incoming encrypted message.
     *
     * For pairing (port 0D0D), call [processPairInitial] instead.
     */
    fun processMessage(model: EquilCmdModel, password: ByteArray, incomingPort: String = ""): EquilCmdModel? {
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: processMessage phase=$phase")
        // Not every command confirms its answer: CmdResistanceGet and CmdStepSet without sendConfig do not, and the app
        // sends the next command on the same link. Taken as a confirmation, that command got the old answer again.
        if (phase == Phase.AWAITING_CONFIRM && !isConfirmation(model, password)) {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: the last answer was not confirmed, this is a new command")
            phase = Phase.AWAITING_COMMAND
        }
        lastResponseWasConfirmationAck = phase == Phase.AWAITING_CONFIRM
        return when (phase) {
            Phase.AWAITING_INITIAL          -> handleInitialRequest(model, password)
            Phase.AWAITING_COMMAND          -> handleCommandData(model, password, incomingPort)
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

    private fun handleCommandData(model: EquilCmdModel, password: ByteArray, incomingPort: String): EquilCmdModel? {
        val contentHex: String
        try {
            contentHex = AESUtil.decrypt(model, password)
        } catch (e: Exception) {
            aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: ignoring stale command (decrypt failed: ${e.message})")
            return null
        }
        val contentBytes = Utils.hexStringToBytes(contentHex)
        val cmdType = if (contentBytes.size >= 6) "%02X,%02X".format(contentBytes[4], contentBytes[5]) else "??"
        val command = commandOf(contentBytes)
        if (take(lostCommands, command)) {
            // The pump never got it: it does nothing and sends nothing. The next command starts a new exchange.
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: $command lost before the pump")
            return null
        }
        synchronized(state) { state.commands++ }
        val responseData = processCommand(contentBytes, incomingPort)
        lastCommandResponse = responseData
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: handleCommandData cmd=$cmdType responseSize=${responseData.size} response=${Utils.bytesToHex(responseData)}")

        phase = Phase.AWAITING_CONFIRM
        if (take(lostAnswers, command)) {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: $command done, its answer is lost")
            return null
        }
        return AESUtil.aesEncrypt(password, responseData)
    }

    /**
     * A confirmation has 0x00 after the 4-byte index (`getNextData` of every command), a command has 0x01 (set) or
     * 0x02 (get) there. A message that cannot be decrypted counts as a confirmation, so [handleConfirmation] deals
     * with it as before.
     */
    private fun isConfirmation(model: EquilCmdModel, password: ByteArray): Boolean {
        val content = runCatching { Utils.hexStringToBytes(AESUtil.decrypt(model, password)) }.getOrNull() ?: return true
        return content.size < 5 || content[4].toInt() == 0x00
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
        awaitingPrime = true

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
     * The resistance and the basal read share 0x02,0x02: the resistance read comes on port 1515.
     */
    private fun processCommand(data: ByteArray, incomingPort: String = ""): ByteArray {
        if (data.size < 6) return indexedResponse()

        val cmdType1 = data[4].toInt() and 0xFF
        val cmdType2 = data[5].toInt() and 0xFF
        // A read must see the reservoir and the bolus as they are now.
        settle()

        return when {
            // --- GET commands (0x02, ...) ---
            cmdType1 == 0x02 && cmdType2 == 0x00 -> processRunningModeGet()
            cmdType1 == 0x02 && cmdType2 == 0x04 -> processTempBasalGet()
            cmdType1 == 0x02 && cmdType2 == 0x07 -> processInsulinGet()
            cmdType1 == 0x02 && cmdType2 == 0x02 -> if (incomingPort.startsWith(RESISTANCE_PORT)) processResistanceGet() else processBasalGet()
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
        // Seen on a real pump (2026-11-08): its activation logged (4,7,0) "prime" with rate 0. UNVERIFIED: that it is
        // written when the fill reaches the piston. Without it a new emulated pump had an empty log, which advertises
        // index 0, and a driver cannot learn anything from that.
        if (awaitingPrime && resistance >= PISTON_REACHED) synchronized(state) {
            awaitingPrime = false
            log(TYPE_PRIME, rate = 0)
        }
        return response
    }

    /** Set by a pairing: the next fill that reaches the piston logs the prime. */
    @Volatile private var awaitingPrime = false

    private fun processBasalGet(): ByteArray {
        // CmdBasalGet: [index(4)][2 unused][48 half hours, low byte first]. The emulator keeps one value per hour.
        val response = ByteArray(6 + 48 * 2)
        putIndex(response)
        for (halfHour in 0 until 48) {
            val value = state.basalRates[halfHour / 2]
            response[6 + 2 * halfHour] = (value and 0xFF).toByte()
            response[7 + 2 * halfHour] = ((value shr 8) and 0xFF).toByte()
        }
        return response
    }

    private fun processHistoryGet(data: ByteArray): ByteArray {
        // CmdHistoryGet.decodeConfirmData format (24 bytes total):
        // [4:index][5:padding][6:year][7:month][8:day][9:hour][10:min][11:sec]
        // [12:battery][13:insulin][14-15:rate][16-17:largeRate][18-19:histIndex]
        // [20:port][21:type][22:level][23:parm]
        val response = ByteArray(24)
        putIndex(response)

        // CmdHistoryGet asks for one entry by its index: [index(4)][0x02,0x01][history index(4)].
        val requested = if (data.size >= 10) getInt(data, 6) else state.historyIndex
        val event = synchronized(state) {
            state.historyEvents.lastOrNull { it.index == requested } ?: state.historyEvents.lastOrNull() ?: HistoryEvent.now(state.historyIndex, 0)
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
        // The driver reads the event from port, type and level. With port 0 no entry was an alarm.
        response[20] = event.port.toByte()
        response[21] = event.type.toByte()
        response[22] = event.level.toByte()
        response[23] = event.parm.toByte()
        return response
    }

    // --- SET handlers ---

    private fun processTempBasalSet(data: ByteArray): ByteArray {
        if (data.size >= 14) synchronized(state) {
            val step = getInt(data, 6)
            if (step > 0 && refusesTempBasal(step)) return refusedResponse()
            settle()
            // Seen on a real pump (2026-11-08): a start logs (4,10,0) and an end (4,11,0), each with the rate before it,
            // then a status entry with the new rate if the rate changed. UNVERIFIED: a temp basal set while one runs;
            // the driver ends the old one first.
            // Seen too: step 47 (0.5875 U/h) ran at 0.6 U/h, step 40 at 0.5 U/h. The pump rounds a temp basal to a grid
            // that is not known; the emulator runs the step as sent.
            val before = basalSpeed()
            if (state.isTempBasalRunning) {
                log(TYPE_TEMP_BASAL_END)
                clearTempBasal()
            }
            if (step > 0) {
                log(TYPE_TEMP_BASAL_START)
                state.tempBasalStep = step
                state.tempBasalDuration = getInt(data, 10)
                state.isTempBasalRunning = true
                state.tempBasalStartTime = System.currentTimeMillis()
            }
            logRateChange(before)
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
                if (synchronized(state) { refusesBolus(field1) }) return refusedResponse()
                state.lastBolusStep = field1
                state.lastBolusStepTime = field2
                state.lastBolusTime = System.currentTimeMillis()
                startBolus(steps = field1, seconds = field2)
            } else if (field3 != 0 || field4 != 0) {
                // CmdExtendedBolusSet. Seen on a real pump (2026-11-10): a status entry with the speed of the extended bolus
                // as largeRate (160 for 0.5 U in 30 min).
                synchronized(state) {
                    state.extendedBolusStep = field3
                    state.extendedBolusDuration = field4
                    state.isExtendedBolusRunning = field3 > 0
                    log(TYPE_STATUS)
                }
            } else {
                // CmdLargeBasalSet(0), the stop of a bolus, and CmdExtendedBolusSet(0) look the same. UNVERIFIED: that the
                // real pump ends both. Seen on a real pump (2026-11-10): the cancel of an extended bolus logs a status entry
                // with largeRate 0.
                stopBolus()
                synchronized(state) {
                    val wasExtended = state.isExtendedBolusRunning
                    state.extendedBolusStep = 0
                    state.extendedBolusDuration = 0
                    state.isExtendedBolusRunning = false
                    if (wasExtended) log(TYPE_STATUS)
                }
            }
        }
        return indexedResponse()
    }

    /**
     * Seen on a real pump (2026-11-08): a bolus logs a status entry at its start, with the bolus speed as largeRate and
     * the basal, which goes on during the bolus, as rate. The history screen of the driver reads a bolus from it.
     * UNVERIFIED: a bolus sent while one runs. The emulator ends the first one where it is.
     */
    private fun startBolus(steps: Int, seconds: Int) = synchronized(state) {
        stopBolus()
        // #5142: 30 U/min ended at once in "abnormal infusion stopped" (real records 1741-1743), 12 U/min worked.
        // UNVERIFIED: where between the two the limit is. The driver promises never to go faster than 12 U/min.
        if (steps / BOLUS_STEPS_PER_UNIT / seconds.coerceAtLeast(1) * 60 > MAX_UNITS_PER_MINUTE + LIMIT_ROUNDING) {
            stopDelivery { log(TYPE_POWER_EVENT, LEVEL_ALARM, port = HistoryEvent.PORT_POWER) }
            return@synchronized
        }
        state.runningBolus = RunningBolus(startedAt = System.currentTimeMillis(), steps = steps, durationMs = seconds * 1000L)
        log(TYPE_STATUS, largeRate = bolusSpeed(steps, seconds))
    }

    /** The bolus ends with what it gave until now. */
    private fun stopBolus() = synchronized(state) {
        settle()
        finishBolus(System.currentTimeMillis())
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
                setRunningMode(getInt(data, 6))
            }
        } else if (data.size >= 10) {
            setRunningMode(getInt(data, 6))
        }
        return indexedResponse()
    }

    /**
     * Seen on a real pump (2026-11-08): a suspend logs (4,5,0) with rate 0. A resume logs only a status entry with the
     * rate it runs again, and that is the temp basal of before the suspend (#3830).
     * UNVERIFIED: whether the time of the temp basal goes on during the suspend. The emulator lets it go on.
     */
    private fun setRunningMode(mode: Int) = synchronized(state) {
        settle()
        val before = basalSpeed()
        val wasRunning = state.runningMode == RUNNING_MODE_RUNNING
        state.runningMode = mode
        if (wasRunning && mode != RUNNING_MODE_RUNNING) log(TYPE_PAUSE)
        else logRateChange(before)
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
            // From now on the pump logs its entries in the time it was given.
            synchronized(state) { state.clockOffsetMs = state.pumpTimeMillis - System.currentTimeMillis() }
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
            val lowAlarmOffset = 14
            val basalOffset = 22
            val bolusOffset = 24
            state.lowAlarmStep = (data[lowAlarmOffset].toInt() and 0xFF) or ((data[lowAlarmOffset + 1].toInt() and 0xFF) shl 8)
            state.basalThresholdStep = (data[basalOffset].toInt() and 0xFF) or ((data[basalOffset + 1].toInt() and 0xFF) shl 8)
            state.bolusThresholdStep = (data[bolusOffset].toInt() and 0xFF) or ((data[bolusOffset + 1].toInt() and 0xFF) shl 8)
        }
        return indexedResponse()
    }

    /**
     * A bolus the pump does not give: it is stopped, or the amount is above the max bolus of CmdSettingSet. The limits are
     * real settings of the pump, so a real pump refuses here too. UNVERIFIED: how it says so, see [refusedResponse].
     */
    private fun refusesBolus(steps: Int): Boolean =
        state.runningMode != RUNNING_MODE_RUNNING ||
            state.bolusThresholdStep > 0 && steps / BOLUS_STEPS_PER_UNIT > state.bolusThresholdStep * SPEED_UNITS_PER_STEP + LIMIT_ROUNDING

    /** A temp basal the pump does not run: it is stopped, or the rate is above the max basal of CmdSettingSet. */
    private fun refusesTempBasal(step: Int): Boolean =
        state.runningMode != RUNNING_MODE_RUNNING ||
            state.basalThresholdStep > 0 && step / TEMP_BASAL_STEPS_PER_UNIT > state.basalThresholdStep * SPEED_UNITS_PER_STEP + LIMIT_ROUNDING

    /**
     * UNVERIFIED: the answer to a command the pump refuses. Commands answer with a status in byte 6 (CmdInsulinChange
     * reads it); a value other than 0 is taken here for a refusal. The driver reads no status for a bolus or a temp basal.
     */
    private fun refusedResponse(): ByteArray = indexedResponse().also { it[6] = STATUS_REFUSED }

    // --- Helpers ---

    /**
     * Build an OK response with the index. 24 bytes to satisfy any decodeConfirmData.
     * It has no resistance in it: the resistance read gets its own answer, see [processMessage].
     */
    private fun indexedResponse(): ByteArray {
        val response = ByteArray(24)
        putIndex(response)
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

    // --- Test hooks, see EquilEmulatorBleTransport ---

    /** The next [command] never reaches the pump. */
    fun loseCommand(command: EmulatedCommand): Boolean = synchronized(lostCommands) { lostCommands.add(command) }

    /** The pump runs the next [command], and its answer is lost. */
    fun loseAnswer(command: EmulatedCommand): Boolean = synchronized(lostAnswers) { lostAnswers.add(command) }

    /** Only the first fault can fire, so faults fire in the order the test set them. */
    private fun take(list: MutableList<EmulatedCommand>, command: EmulatedCommand?): Boolean =
        command != null && synchronized(list) { list.firstOrNull() == command && list.removeAt(0) == command }

    /** What the pump does for [data], by its type and fields. Null for a command no hook names. */
    private fun commandOf(data: ByteArray): EmulatedCommand? {
        // A read is only the index and the two type bytes, 6 bytes. A setting carries its values after them.
        if (data.size < 6) return null
        val type1 = data[4].toInt() and 0xFF
        val type2 = data[5].toInt() and 0xFF
        return when {
            type1 == 0x01 && type2 == 0x03 && data.size >= 22 ->
                when {
                    getInt(data, 6) != 0 || getInt(data, 10) != 0  -> EmulatedCommand.BOLUS_START
                    getInt(data, 14) != 0 || getInt(data, 18) != 0 -> EmulatedCommand.EXTENDED_BOLUS_START
                    // The same zeros stop a bolus and an extended bolus: the pump stops what runs.
                    state.runningBolus == null && state.isExtendedBolusRunning -> EmulatedCommand.EXTENDED_BOLUS_STOP
                    else                                            -> EmulatedCommand.BOLUS_STOP
                }

            type1 == 0x01 && type2 == 0x04 && data.size >= 10   -> if (getInt(data, 6) > 0) EmulatedCommand.TEMP_BASAL_START else EmulatedCommand.TEMP_BASAL_STOP
            type1 == 0x01 && type2 == 0x02 && data.size >= 10   -> EmulatedCommand.BASAL_PROFILE
            type1 == 0x02 && type2 == 0x00                      -> EmulatedCommand.STATUS
            // The temp basal read, by which the driver checks a temp basal command that got no answer.
            type1 == 0x02 && type2 == 0x04                      -> EmulatedCommand.STATUS
            else                                                -> null
        }
    }

    /** What the pump holds now. @param linkUp a phone is connected, which only the transport knows */
    fun snapshot(linkUp: Boolean): EmulatedPumpState = synchronized(state) {
        // It also ends a temp basal whose time is over.
        settle()
        EmulatedPumpState(
            boluses = state.deliveredBoluses.toList(),
            bolusRunning = state.runningBolus != null,
            // A suspend keeps the temp basal, and a resume runs it again (#3830): only while the pump runs is it given.
            tempBasal = if (state.isTempBasalRunning && state.runningMode == RUNNING_MODE_RUNNING)
                EmulatedTempBasal(absolute = state.tempBasalStep / TEMP_BASAL_STEPS_PER_UNIT, percent = null) else null,
            suspended = state.runningMode != RUNNING_MODE_RUNNING,
            reservoir = state.reservoir,
            linkUp = linkUp,
            commands = state.commands,
            // UNVERIFIED: that the pump gives an extended bolus while it is suspended; the emulator reports it only while it runs.
            extendedBolus = if (state.isExtendedBolusRunning && state.runningMode == RUNNING_MODE_RUNNING)
                EmulatedExtendedBolus(units = state.extendedBolusStep / BOLUS_STEPS_PER_UNIT, minutes = state.extendedBolusDuration / SECONDS_PER_MINUTE) else null,
            battery = state.battery,
            // Each hour holds what the pump gives in half an hour, in 0.00625 U steps: 96 is 1.2 U/h, as on a real pump.
            basal = state.basalRates.map { it * BASAL_UNITS_PER_HOUR_PER_VALUE }
        )
    }

    /** All the pump holds, with the bolus it gives, for a test that kills the app. Read back with [restore]. */
    fun saveState(): ByteArray = synchronized(state) {
        settle()
        ByteArrayOutputStream().also { bytes -> ObjectOutputStream(bytes).use { it.writeObject(state) } }.toByteArray()
    }

    /** The newest log entry, or null, and the basal speed now: what the pump advertises. */
    fun advertised(): Pair<HistoryEvent?, Int> = synchronized(state) {
        settle()
        state.historyEvents.lastOrNull() to basalSpeed()
    }

    /**
     * The pump reports [alarm] now, with the log entries a real pump writes for it, as in the real records in
     * `EquilManagerTest` of the driver. False for an alarm this pump has not.
     */
    fun raise(alarm: EmulatedAlarm): Boolean = synchronized(state) {
        settle()
        when (alarm) {
            EmulatedAlarm.LOW_RESERVOIR    -> log(TYPE_RESERVOIR, LEVEL_WARNING)
            // Real records 1711-1717: the temp basal ends, the reservoir is empty, the pump rewinds and stops.
            EmulatedAlarm.EMPTY_RESERVOIR  -> {
                state.reservoir = 0.0
                stopDelivery { log(TYPE_RESERVOIR, LEVEL_ALARM) }
            }

            EmulatedAlarm.OCCLUSION        -> blockage(System.currentTimeMillis())
            EmulatedAlarm.LOW_BATTERY      -> log(TYPE_BATTERY, LEVEL_WARNING, port = HistoryEvent.PORT_POWER)
            EmulatedAlarm.STOPPED_TOO_LONG -> return@synchronized false
        }
        true
    }

    /**
     * Brings the pump up to now: the bolus steps due by now are given, and a bolus or a temp basal whose time is over
     * ends, with its log entry at that time. The pump clock is the phone clock, so this is all the pump needs.
     */
    private fun settle() = synchronized(state) {
        val now = System.currentTimeMillis()
        // The time since the last look: the basal used insulin, and the pump used battery.
        val since = state.lastSettleAt
        state.lastSettleAt = now
        if (since in 1 until now) {
            takeInsulin(basalSpeed(since) * SPEED_UNITS_PER_STEP * (now - since) / MS_PER_HOUR)
            useBattery(now - since)
        }
        state.runningBolus?.let { bolus ->
            val elapsed = (now - bolus.startedAt).coerceAtLeast(0)
            val due = if (elapsed >= bolus.durationMs) bolus.steps else (bolus.steps.toLong() * elapsed / bolus.durationMs).toInt()
            give(bolus, due)
            if (elapsed >= bolus.durationMs) finishBolus(at = bolus.startedAt + bolus.durationMs)
        }
        if (state.isTempBasalRunning && now >= tempBasalEnd()) {
            // UNVERIFIED: that a temp basal whose time is over logs as a cancel does, see processTempBasalSet.
            val end = tempBasalEnd()
            val before = basalSpeed(end - 1)
            log(TYPE_TEMP_BASAL_END, at = end, rate = before)
            clearTempBasal()
            logRateChange(before, at = end)
        }
    }

    /** Gives the bolus steps due up to [upTo], as far as the reservoir has insulin for them. */
    private fun give(bolus: RunningBolus, upTo: Int) {
        val more = (upTo - bolus.givenSteps).coerceAtLeast(0).coerceAtMost((state.reservoir * BOLUS_STEPS_PER_UNIT).toInt())
        bolus.givenSteps += more
        takeInsulin(more / BOLUS_STEPS_PER_UNIT)
    }

    /**
     * Takes [units] from the reservoir. Going below the reservoir alarm of CmdSettingSet the pump logs (4,1,1); at 0 it
     * stops all delivery, as in the real records 1711-1717.
     */
    private fun takeInsulin(units: Double) {
        if (units <= 0) return
        val before = state.reservoir
        state.reservoir = (before - units).coerceAtLeast(0.0)
        val low = state.lowAlarmStep * SPEED_UNITS_PER_STEP
        if (before > low && state.reservoir <= low && state.reservoir > 0) log(TYPE_RESERVOIR, LEVEL_WARNING)
        if (before > 0 && state.reservoir <= 0) stopDelivery { log(TYPE_RESERVOIR, LEVEL_ALARM) }
    }

    /** UNVERIFIED: how fast the battery goes down. One percent per hour the pump runs. */
    private fun useBattery(ms: Long) {
        state.batteryUseMs += ms
        val percents = (state.batteryUseMs / BATTERY_MS_PER_PERCENT).toInt()
        if (percents == 0) return
        state.batteryUseMs -= percents * BATTERY_MS_PER_PERCENT
        setBattery(state.battery - percents)
    }

    /**
     * Sets the battery, with the entries a real pump writes. UNVERIFIED: the level of the low battery warning (5,0,1).
     * At 0 the real records 1561-1564: battery empty, the temp basal ends, a status with rate 0, rewind; delivery stops.
     */
    fun setBattery(percent: Int) = synchronized(state) {
        val before = state.battery
        state.battery = percent.coerceIn(0, 100)
        if (before > BATTERY_LOW_PERCENT && state.battery <= BATTERY_LOW_PERCENT && state.battery > 0) log(TYPE_BATTERY, LEVEL_WARNING, port = HistoryEvent.PORT_POWER)
        if (before > 0 && state.battery == 0) {
            finishBolus(System.currentTimeMillis(), logEnd = false)
            log(TYPE_BATTERY, LEVEL_ALARM, port = HistoryEvent.PORT_POWER)
            if (state.isTempBasalRunning) {
                log(TYPE_TEMP_BASAL_END)
                clearTempBasal()
            }
            state.runningMode = RUNNING_MODE_STOP
            log(TYPE_STATUS)
            log(TYPE_REWIND)
        }
    }

    /** The pump clock goes [offsetMs] further off the phone clock, as a clock that drifted or was not set. */
    fun shiftClock(offsetMs: Long): Boolean = synchronized(state) {
        state.clockOffsetMs += offsetMs
        true
    }

    /**
     * @param logEnd the entry at the end of a bolus, seen on a real pump (2026-11-08): a status entry with largeRate 0, at
     * the end or at the second the stop came. UNVERIFIED: an entry that stops delivery ends a bolus by itself and needs none.
     */
    private fun finishBolus(at: Long, logEnd: Boolean = true) {
        val bolus = state.runningBolus ?: return
        state.deliveredBoluses.add(bolus.givenSteps / BOLUS_STEPS_PER_UNIT)
        state.runningBolus = null
        if (logEnd) log(TYPE_STATUS, at = at)
    }

    /** UNVERIFIED: a blockage ends the bolus, and the pump logs (4,2,2). Whether it stops the basal too is not known. */
    private fun blockage(at: Long) {
        finishBolus(at, logEnd = false)
        log(TYPE_BLOCKAGE, LEVEL_ALARM, at = at)
    }

    /** All delivery stops now: the bolus and the temp basal end, the pump logs [event], rewinds and stops. */
    private fun stopDelivery(event: () -> Unit) {
        finishBolus(System.currentTimeMillis(), logEnd = false)
        if (state.isTempBasalRunning) {
            log(TYPE_TEMP_BASAL_END)
            clearTempBasal()
        }
        event()
        log(TYPE_REWIND)
        state.runningMode = RUNNING_MODE_STOP
    }

    private fun tempBasalEnd(): Long = state.tempBasalStartTime + state.tempBasalDuration * 1000L

    private fun clearTempBasal() {
        state.isTempBasalRunning = false
        state.tempBasalStep = 0
        state.tempBasalDuration = 0
    }

    /**
     * Adds an entry to the pump log as the pump writes one: the next index, the time, the battery, the reservoir and the
     * rate. UNVERIFIED: a status entry while a bolus runs, for a basal change, has the speed of the bolus as largeRate.
     */
    private fun log(
        type: Int, level: Int = 0, port: Int = HistoryEvent.PORT_STATUS,
        largeRate: Int = if (type == TYPE_STATUS && port == HistoryEvent.PORT_STATUS) runningBolusSpeed() else 0,
        at: Long = System.currentTimeMillis(), rate: Int = basalSpeed(at)
    ) {
        state.historyIndex++
        state.historyEvents.add(
            // In the pump's own time.
            HistoryEvent.at(
                at + state.clockOffsetMs, index = state.historyIndex, type = type, level = level, port = port, rate = rate, largeRate = largeRate,
                battery = state.battery, insulin = state.currentInsulin
            )
        )
    }

    /**
     * Seen on a real pump (2026-11-08): a status entry is written when the basal changes, with the new rate. A temp basal
     * at the rate of the base basal writes none.
     */
    private fun logRateChange(before: Int, at: Long = System.currentTimeMillis()) {
        if (basalSpeed(at) != before) log(TYPE_STATUS, at = at)
    }

    /** The basal the pump gives at [at], in steps of 0.00625 U/h, as the log and the advertisement show it. */
    private fun basalSpeed(at: Long = System.currentTimeMillis()): Int = when {
        state.runningMode != RUNNING_MODE_RUNNING           -> 0
        state.isTempBasalRunning && at < tempBasalEnd()     -> (state.tempBasalStep / TEMP_BASAL_STEPS_PER_UNIT / SPEED_UNITS_PER_STEP).roundToInt()
        else                                                -> state.basalRates[Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.HOUR_OF_DAY)]
    }

    /** UNVERIFIED: that a status entry during an extended bolus, for a basal change, has its speed too. */
    private fun runningBolusSpeed(): Int = state.runningBolus?.let { bolusSpeed(it.steps, (it.durationMs / 1000).toInt()) }
        ?: if (state.isExtendedBolusRunning) bolusSpeed(state.extendedBolusStep, state.extendedBolusDuration) else 0

    /**
     * The bolus speed in steps of 0.00625 U/h, as the log shows it. Seen on a real pump (2026-11-08): the log has two bytes
     * for it and keeps the low 16 bits, so 12 U/min (115200) shows as 49664.
     */
    private fun bolusSpeed(steps: Int, seconds: Int): Int =
        (steps / BOLUS_STEPS_PER_UNIT / seconds.coerceAtLeast(1) * 3600 / SPEED_UNITS_PER_STEP).roundToInt() and 0xFFFF

    companion object {

        /** Log entries on port 4, from the real records in `EquilManagerTest` and from `PumpEvent` of the driver. */
        private const val TYPE_STATUS = 0
        private const val TYPE_RESERVOIR = 1
        private const val TYPE_BLOCKAGE = 2
        private const val TYPE_PAUSE = 5
        private const val TYPE_PRIME = 7
        private const val TYPE_REWIND = 8

        /** CmdResistanceGet: from this on the piston reached the insulin. */
        private const val PISTON_REACHED = 500
        private const val TYPE_TEMP_BASAL_START = 10
        private const val TYPE_TEMP_BASAL_END = 11

        /** On port 5. TYPE_POWER_EVENT with level 0 is a power-on reset, with level 2 "abnormal infusion stopped". */
        private const val TYPE_BATTERY = 0
        private const val TYPE_POWER_EVENT = 1
        private const val LEVEL_WARNING = 1
        private const val LEVEL_ALARM = 2

        /** The fastest bolus that worked on a real pump (#5142). */
        private const val MAX_UNITS_PER_MINUTE = 12.0

        /** A limit holds, also for a value that is on it but off by floating point. */
        private const val LIMIT_ROUNDING = 1e-9

        /** UNVERIFIED, see refusedResponse. */
        private const val STATUS_REFUSED: Byte = 1

        /** UNVERIFIED, see setBattery and useBattery. */
        private const val BATTERY_LOW_PERCENT = 20
        private const val BATTERY_MS_PER_PERCENT = 3_600_000L

        private const val MS_PER_HOUR = 3_600_000.0

        /** The rates in the log and the advertisement count in these. */
        private const val SPEED_UNITS_PER_STEP = 0.00625

        /** A basal value is half an hour of insulin in 0.00625 U steps, so one step of it is 0.0125 U/h. */
        private const val BASAL_UNITS_PER_HOUR_PER_VALUE = 0.0125

        /** The run mode after an empty reservoir, as the driver reads it: STOP. */
        private const val RUNNING_MODE_STOP = 2

        /** The port CmdResistanceGet sends on. */
        private const val RESISTANCE_PORT = "1515"

        /** CmdLargeBasalSet: 160 steps per unit. */
        private const val BOLUS_STEPS_PER_UNIT = 160.0

        /** CmdExtendedBolusSet sends its time in seconds. */
        private const val SECONDS_PER_MINUTE = 60

        /** CmdTempBasalSet: the rate in U/h times 80. */
        private const val TEMP_BASAL_STEPS_PER_UNIT = 80.0

        /** The running mode the pump delivers in. 0 is suspended, 2 stopped, as the driver reads them. */
        private const val RUNNING_MODE_RUNNING = 1

        /** The emulated pump of a test run, kept outside the graph, see [PumpEmulators]. */
        val KEY = PumpEmulators.Key<EquilPumpEmulator>("equil")

        /** The pump of [saveState], in a new process. */
        fun restore(saved: ByteArray, aapsLogger: AAPSLogger? = null): EquilPumpEmulator =
            EquilPumpEmulator(ObjectInputStream(ByteArrayInputStream(saved)).use { it.readObject() } as EquilPumpState, aapsLogger)
    }
}
