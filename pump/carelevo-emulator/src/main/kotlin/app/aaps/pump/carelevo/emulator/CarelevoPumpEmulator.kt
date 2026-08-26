package app.aaps.pump.carelevo.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.experimental.xor
import kotlin.math.roundToInt

/**
 * Answers CareLevo BLE commands the way a real patch does, against a live [CarelevoPumpState].
 *
 * The CareLevo wire has no framing, length prefix, CRC or encryption: a request is
 * `[opcode][payload]` written verbatim, and a response is a raw notification whose byte 0 is the
 * opcode the client correlates on. So [handle] is the whole protocol — it takes one written frame
 * and returns the notification frames the patch would send back, in order.
 *
 * Returning a *list* is what makes the two irregular shapes fall out naturally: the 0x33 patch-info
 * report is two frames (0x93 + 0x94), and the 0x12 safety check streams N progress frames before its
 * terminal one. An empty list means the patch stays silent.
 *
 * Response opcodes are a hardcoded table, never computed. The rule is *mostly* `request + 0x60`
 * (arithmetic — `or 0x60` breaks every opcode from 0x21 up), but there are real exceptions: 0x19→0x7A
 * and 0x1A→0x79 are swapped, and 0x4B answers on 0xBB.
 */
class CarelevoPumpEmulator(
    val state: CarelevoPumpState = CarelevoPumpState(),
    private val aapsLogger: AAPSLogger? = null
) {

    /** Frames the patch sends back for [request]; empty when it stays silent. */
    fun handle(request: ByteArray): List<ByteArray> {
        if (request.isEmpty()) return emptyList()
        val opcode = request[0]
        if (opcode in state.silentOpcodes) {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: silently dropping 0x${hex(opcode)}")
            return emptyList()
        }
        val responses = dispatch(opcode, request)
        aapsLogger?.debug(
            LTag.PUMPEMULATOR,
            "emulator: 0x${hex(opcode)} -> ${responses.joinToString(", ") { "0x${hex(it[0])}" }.ifEmpty { "(silence)" }}"
        )
        return responses
    }

    private fun dispatch(opcode: Byte, request: ByteArray): List<ByteArray> = when (opcode) {
        OP_SET_TIME            -> setTime(request)
        OP_SAFETY_CHECK        -> safetyCheck()
        OP_BASAL_SET           -> basalProgram(request, RESP_BASAL_SET)
        OP_BASAL_UPDATE        -> basalProgram(request, RESP_BASAL_UPDATE)
        OP_NOTICE_THRESHOLD    -> noticeThreshold(request)
        OP_INFUSION_THRESHOLD  -> infusionThreshold(request)
        OP_BUZZ_MODE           -> buzzMode(request)
        OP_NEEDLE_ACK          -> needleAck(request)
        OP_NEEDLE_STATUS       -> needleStatus()
        OP_THRESHOLD_SETUP     -> thresholdSetup(request)
        OP_ADDITIONAL_PRIMING  -> additionalPriming()
        OP_TEMP_BASAL          -> tempBasal()
        OP_IMMEDIATE_BOLUS     -> immediateBolus(request)
        OP_EXTEND_BOLUS        -> extendBolus(request)
        OP_PUMP_STOP           -> pumpStop(request)
        OP_PUMP_RESUME         -> pumpResume(request)
        OP_EXTEND_BOLUS_CANCEL -> extendBolusCancel()
        OP_BOLUS_CANCEL        -> bolusCancel()
        OP_TEMP_BASAL_CANCEL   -> tempBasalCancel()
        OP_INFUSION_INFO       -> infusionInfo(request)
        OP_PATCH_INFO          -> patchInfoFrames()
        OP_PATCH_DISCARD       -> patchDiscard()
        OP_MAC_ADDRESS         -> macAddress(request)
        OP_ALARM_CLEAR         -> alarmClear(request)
        OP_ALERT_ALARM_SET     -> alertAlarmSet(request)
        OP_APP_AUTH            -> appAuth(request)
        else                   -> {
            aapsLogger?.debug(LTag.PUMPEMULATOR, "emulator: unknown opcode 0x${hex(opcode)}")
            emptyList()
        }
    }

    // ---- Pairing ----------------------------------------------------------------------------

    /** `[0x9B][mac 6][checksum ..]`. The key is retained so [appAuth] can verify the app's fold. */
    private fun macAddress(request: ByteArray): List<ByteArray> {
        state.lastMacKey = if (request.size > 1) request.u(1) else null
        return listOf(byteArrayOf(RESP_MAC_ADDRESS) + state.macAddress + state.macCheckSum)
    }

    /**
     * Verifies the app's handshake rather than rubber-stamping it: the app XOR-folds
     * `(mac || checksum)` starting from the key it sent on 0x3B, so the same fold here must match.
     * A real patch rejects a wrong fold, and so does this — that is the point of emulating it.
     */
    private fun appAuth(request: ByteArray): List<ByteArray> {
        val key = state.lastMacKey
        val expected = key?.let { (state.macAddress + state.macCheckSum).fold(it.toByte()) { acc, b -> acc xor b } }
        val offered = if (request.size > 1) request[1] else null
        val result = when {
            state.resultCodeOverrides.containsKey(OP_APP_AUTH) -> state.resultFor(OP_APP_AUTH)
            expected == null || offered == null                -> AUTH_FAILED
            offered != expected                               -> {
                aapsLogger?.debug(
                    LTag.PUMPEMULATOR,
                    "emulator: auth rejected, expected 0x${hex(expected)} got 0x${hex(offered)}"
                )
                AUTH_FAILED
            }

            else                                              -> CarelevoPumpState.RESULT_SUCCESS
        }
        return listOf(byteArrayOf(RESP_APP_AUTH, result.toByte()))
    }

    // ---- Time and patch info ----------------------------------------------------------------

    /**
     * 0x11 carries a byte-identical frame for two different commands; only `subId` (byte 1) tells
     * them apart. `subId == 0` is the activation path, which waits for the patch-info pair and would
     * hang on a 0x71; every other subId is a plain set-time that wants the 0x71 ack.
     */
    private fun setTime(request: ByteArray): List<ByteArray> {
        val subId = if (request.size > 1) request.u(1) else 0
        val result = state.resultFor(OP_SET_TIME)
        if (request.size >= SET_TIME_LENGTH && result == CarelevoPumpState.RESULT_SUCCESS) {
            state.subId = subId
            state.runningMinutes = 0
        }
        return if (subId == 0) patchInfoFrames()
        else listOf(byteArrayOf(RESP_SET_TIME, result.toByte()))
    }

    /**
     * RPT1 `[0x93][result][serial 13 ASCII]` and RPT2 `[0x94][result][fw 4 ASCII][unused 5][model 5]`.
     * Order is irrelevant — the client keys them by opcode — but both must arrive or the request hangs.
     */
    private fun patchInfoFrames(): List<ByteArray> {
        val result = state.resultFor(OP_PATCH_INFO).toByte()
        val rpt1 = byteArrayOf(RESP_PATCH_INFO_RPT1, result) + state.serialNumber.ascii(SERIAL_LENGTH)
        val rpt2 = byteArrayOf(RESP_PATCH_INFO_RPT2, result) +
            state.firmwareVersion.ascii(FIRMWARE_LENGTH) +
            ByteArray(RPT2_UNUSED_LENGTH) +
            state.modelBytes.copyOf(MODEL_LENGTH)
        return listOf(rpt1, rpt2)
    }

    // ---- Safety check -----------------------------------------------------------------------

    /**
     * Streams progress frames then a terminal one, all on 0x72. A frame is progress purely because
     * its result byte is 4 or 18; anything else — including 0 for success — ends the stream.
     *
     * Frames are 6 bytes. Never emit 5: `SafetyCheckCommand.decode` reads index 5 whenever
     * `size > 4`, so a 5-byte frame throws out of bounds instead of failing cleanly.
     */
    private fun safetyCheck(): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        repeat(state.safetyCheckProgressFrames) { frames += safetyFrame(SAFETY_PROGRESS_RESULT) }
        frames += safetyFrame(state.safetyCheckResult)
        if (state.safetyCheckResult == CarelevoPumpState.RESULT_SUCCESS) state.pumpStateRaw = PUMP_STATE_RUNNING
        return frames
    }

    private fun safetyFrame(result: Int): ByteArray = byteArrayOf(
        RESP_SAFETY_CHECK,
        result.toByte(),
        (state.safetyCheckVolume / HUNDRED).toByte(),
        (state.safetyCheckVolume % HUNDRED).toByte(),
        (state.safetyCheckDurationSeconds / SECONDS_PER_MINUTE).toByte(),
        (state.safetyCheckDurationSeconds % SECONDS_PER_MINUTE).toByte()
    )

    // ---- Basal ------------------------------------------------------------------------------

    /**
     * Each sequence is its own round-trip: the ack carries no seqNo, so the pump simply banks the
     * segments and answers `[resp][result]`. The program only counts as set once seq 2 lands.
     */
    private fun basalProgram(request: ByteArray, responseOpcode: Byte): List<ByteArray> {
        val seqNo = if (request.size > 1) request.u(1) else 0
        val speeds = (BASAL_SEGMENT_START until request.size step 2)
            .filter { it + 1 < request.size }
            .map { request.u(it) + request.u(it + 1) / CENTI }
        val requestOpcode = request[0]
        val result = state.resultFor(requestOpcode)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.basalSegmentsBySeq[seqNo] = speeds
            if (seqNo == BASAL_FINAL_SEQ) state.basalProgramCommitted = true
        }
        return listOf(byteArrayOf(responseOpcode, result.toByte()))
    }

    // ---- Settings ---------------------------------------------------------------------------

    /** `[0x75][type]` — byte 1 echoes the type; there is no result code, arrival means success. */
    private fun noticeThreshold(request: ByteArray): List<ByteArray> {
        val type = if (request.size > 1) request.u(1) else 0
        val value = if (request.size > 2) request.u(2) else 0
        when (type) {
            NOTICE_TYPE_LOW_INSULIN -> state.lowInsulinNotice = value
            NOTICE_TYPE_EXPIRY      -> state.expiryNotice = value
        }
        return listOf(byteArrayOf(RESP_NOTICE_THRESHOLD, type.toByte()))
    }

    /** `[0x77][type][result]` — result sits at index 2 here, because byte 1 echoes the type. */
    private fun infusionThreshold(request: ByteArray): List<ByteArray> {
        val flag = if (request.size > 1) request[1] else 0
        val result = state.resultFor(OP_INFUSION_THRESHOLD)
        if (request.size >= 4 && result == CarelevoPumpState.RESULT_SUCCESS) {
            val value = request.u(2) + request.u(3) / CENTI
            if (flag == FLAG_MAX_VOLUME) state.maxBolusDose = value else state.maxBasalSpeed = value
        }
        return listOf(byteArrayOf(RESP_INFUSION_THRESHOLD, flag, result.toByte()))
    }

    private fun buzzMode(request: ByteArray): List<ByteArray> {
        val result = state.resultFor(OP_BUZZ_MODE)
        if (request.size > 1 && result == CarelevoPumpState.RESULT_SUCCESS) state.buzzUse = request[1] == FLAG_ON
        return listOf(byteArrayOf(RESP_BUZZ_MODE, result.toByte()))
    }

    private fun thresholdSetup(request: ByteArray): List<ByteArray> {
        val result = state.resultFor(OP_THRESHOLD_SETUP)
        if (request.size >= THRESHOLD_SETUP_LENGTH && result == CarelevoPumpState.RESULT_SUCCESS) {
            state.insulinRemainsThreshold = request.u(1)
            state.expiryThreshold = request.u(2)
            state.maxBasalSpeed = request.u(3) + request.u(4) / CENTI
            state.maxBolusDose = request.u(5) + request.u(6) / CENTI
            state.buzzUse = request[7] == FLAG_ON
        }
        return listOf(byteArrayOf(RESP_THRESHOLD_SETUP, result.toByte()))
    }

    private fun alertAlarmSet(request: ByteArray): List<ByteArray> {
        val result = state.resultFor(OP_ALERT_ALARM_SET)
        if (request.size > 1 && result == CarelevoPumpState.RESULT_SUCCESS) state.alertAlarmMode = request.u(1)
        return listOf(byteArrayOf(RESP_ALERT_ALARM_SET, result.toByte()))
    }

    /** `[0xA7][subId][cause][result]` — result at index 3. */
    private fun alarmClear(request: ByteArray): List<ByteArray> {
        val alarmType = if (request.size > 1) request[1] else 0
        val cause = if (request.size > 2) request[2] else 0
        return listOf(byteArrayOf(RESP_ALARM_CLEAR, alarmType, cause, state.resultFor(OP_ALARM_CLEAR).toByte()))
    }

    // ---- Needle and priming -----------------------------------------------------------------

    /** 0x19 answers on 0x7A — swapped with [needleStatus]. Request byte 1: 0x00 success, 0x01 failure. */
    private fun needleAck(request: ByteArray): List<ByteArray> {
        val result = state.resultFor(OP_NEEDLE_ACK)
        if (request.size > 1 && request[1] == NEEDLE_FLAG_SUCCESS && result == CarelevoPumpState.RESULT_SUCCESS) {
            state.needleInserted = true
        }
        return listOf(byteArrayOf(RESP_NEEDLE_ACK, result.toByte()))
    }

    /** 0x1A answers on 0x79 — swapped with [needleAck]. */
    private fun needleStatus(): List<ByteArray> =
        listOf(byteArrayOf(RESP_NEEDLE_STATUS, state.resultFor(OP_NEEDLE_STATUS).toByte()))

    private fun additionalPriming(): List<ByteArray> {
        val result = state.resultFor(OP_ADDITIONAL_PRIMING)
        if (result == CarelevoPumpState.RESULT_SUCCESS) state.primingCount++
        return listOf(byteArrayOf(RESP_ADDITIONAL_PRIMING, result.toByte()))
    }

    // ---- Delivery ---------------------------------------------------------------------------

    /**
     * The ack is result-only, so the emulator does not need to distinguish the two modes — worth
     * knowing that they are told apart by frame length alone (6 bytes BY_UNIT with a trailing 0x00,
     * 5 bytes BY_PERCENT), since the opcode and layout are otherwise identical.
     */
    private fun tempBasal(): List<ByteArray> {
        val result = state.resultFor(OP_TEMP_BASAL)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.tempBasalRunning = true
            state.modeRaw = MODE_TEMP_BASAL
        }
        return listOf(byteArrayOf(RESP_TEMP_BASAL, result.toByte()))
    }

    private fun tempBasalCancel(): List<ByteArray> {
        val result = state.resultFor(OP_TEMP_BASAL_CANCEL)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.tempBasalRunning = false
            state.modeRaw = MODE_BASAL
        }
        return listOf(byteArrayOf(RESP_TEMP_BASAL_CANCEL, result.toByte()))
    }

    /**
     * `[0x84][actionId][result][mm][ss][remain 3]`. Byte 1 **must** echo the request's actionId —
     * the client correlates on it and silently discards a frame that does not match, hanging the call.
     */
    private fun immediateBolus(request: ByteArray): List<ByteArray> {
        val actionId = if (request.size > 1) request[1] else 0
        val volume = if (request.size > 3) request.u(2) + request.u(3) / CENTI else 0.0
        val result = state.resultFor(OP_IMMEDIATE_BOLUS)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.activeBolusActionId = actionId.toUByte().toInt()
            state.modeRaw = MODE_IMME_BOLUS
            state.insulinRemaining = (state.insulinRemaining - volume).coerceAtLeast(0.0)
            state.infusedTotalBolus += volume
            // The emulated bolus lands immediately rather than ramping, so the whole dose counts as
            // infused — a later cancel must report that, not 0 U for insulin the totals already hold.
            state.bolusInfusedAmount = volume
        }
        val seconds = (volume * SECONDS_PER_UNIT).roundToInt()
        return listOf(
            byteArrayOf(
                RESP_IMMEDIATE_BOLUS,
                actionId,
                result.toByte(),
                (seconds / SECONDS_PER_MINUTE).toByte(),
                (seconds % SECONDS_PER_MINUTE).toByte()
            ) + encodeHundredsUnitsCenti(state.insulinRemaining)
        )
    }

    /** `[0x8C][result][units][centi]` — the infused amount is 2 bytes here, with no hundreds byte. */
    private fun bolusCancel(): List<ByteArray> {
        val result = state.resultFor(OP_BOLUS_CANCEL)
        val infused = state.bolusInfusedAmount
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.activeBolusActionId = null
            state.modeRaw = MODE_BASAL
        }
        return listOf(byteArrayOf(RESP_BOLUS_CANCEL, result.toByte()) + encodeUnitsCenti(infused))
    }

    /**
     * `[0x85][result][mm][ss]`. The decoder recombines bytes 2..3 as `[2] * 60 + [3]` **seconds**, so
     * the requested duration goes into byte 2 as whole minutes — halving it again by 60 would report
     * a 90-minute bolus as 90 seconds.
     *
     * Byte 2 caps the reportable duration at 255 minutes; a longer request is clamped rather than
     * silently wrapping, which is the protocol's limit and not something the emulator can widen.
     */
    private fun extendBolus(request: ByteArray): List<ByteArray> {
        val result = state.resultFor(OP_EXTEND_BOLUS)
        val hour = if (request.size > 5) request.u(5) else 0
        val min = if (request.size > 6) request.u(6) else 0
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.extendedBolusRunning = true
            state.extendedInfusedAmount = 0.0
            state.modeRaw = MODE_EXTEND_BOLUS
        }
        val totalMinutes = (hour * MINUTES_PER_HOUR + min).coerceAtMost(MAX_REPORTABLE_MINUTES)
        return listOf(byteArrayOf(RESP_EXTEND_BOLUS, result.toByte(), totalMinutes.toByte(), 0))
    }

    private fun extendBolusCancel(): List<ByteArray> {
        val result = state.resultFor(OP_EXTEND_BOLUS_CANCEL)
        val infused = state.extendedInfusedAmount
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.extendedBolusRunning = false
            state.modeRaw = MODE_BASAL
        }
        return listOf(byteArrayOf(RESP_EXTEND_BOLUS_CANCEL, result.toByte()) + encodeUnitsCenti(infused))
    }

    // ---- Stop / resume / discard ------------------------------------------------------------

    private fun pumpStop(request: ByteArray): List<ByteArray> {
        val result = state.resultFor(OP_PUMP_STOP)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.stopped = true
            if (request.size > 3) state.subId = request.u(3)
        }
        return listOf(byteArrayOf(RESP_PUMP_STOP, result.toByte()))
    }

    /** `[0x87][result][mode]` — the optional 4th subId byte is left off; the decoder defaults it to 0. */
    private fun pumpResume(request: ByteArray): List<ByteArray> {
        val mode = if (request.size > 1) request[1] else 0
        val result = state.resultFor(OP_PUMP_RESUME)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.stopped = false
            state.modeRaw = MODE_BASAL
        }
        return listOf(byteArrayOf(RESP_PUMP_RESUME, result.toByte(), mode))
    }

    private fun patchDiscard(): List<ByteArray> {
        val result = state.resultFor(OP_PATCH_DISCARD)
        if (result == CarelevoPumpState.RESULT_SUCCESS) {
            state.discarded = true
            state.pumpStateRaw = PUMP_STATE_READY
        }
        return listOf(byteArrayOf(RESP_PATCH_DISCARD, result.toByte()))
    }

    // ---- Status -----------------------------------------------------------------------------

    /**
     * The 20-byte status frame. Bytes 13..14 are documented but never decoded — they still have to be
     * present, because the decoder reads index 19 and requires the full length.
     */
    private fun infusionInfo(request: ByteArray): List<ByteArray> {
        val inquiryType = if (request.size > 1) request.u(1) else 0
        val frame = ByteArray(INFUSION_INFO_LENGTH)
        frame[0] = RESP_INFUSION_INFO
        frame[1] = inquiryType.toByte()
        frame[2] = (state.runningMinutes / MINUTES_PER_HOUR).toByte()
        frame[3] = (state.runningMinutes % MINUTES_PER_HOUR).toByte()
        encodeHundredsUnitsCenti(state.insulinRemaining).copyInto(frame, 4)
        encodeUnitsCenti(state.infusedTotalBasal).copyInto(frame, 7)
        encodeUnitsCenti(state.infusedTotalBolus).copyInto(frame, 9)
        frame[11] = state.pumpStateRaw.toByte()
        frame[12] = state.modeRaw.toByte()
        // 13..14 (infuseSetMinutes) intentionally left zero — present but never read.
        encodeUnitsCenti(state.infusedTotalBolus).copyInto(frame, 15)
        frame[17] = (state.runningMinutes / MINUTES_PER_HOUR).toByte()
        frame[18] = (state.runningMinutes % MINUTES_PER_HOUR).toByte()
        frame[19] = 0
        return listOf(frame)
    }

    // ---- Encoding helpers -------------------------------------------------------------------

    /** `[hundreds, units, centi]` — the 3-byte reservoir encoding of 0x91[4..6] and 0x84[5..7]. */
    private fun encodeHundredsUnitsCenti(value: Double): ByteArray {
        val centiTotal = (value * HUNDRED).roundToInt().coerceAtLeast(0)
        return byteArrayOf(
            (centiTotal / TEN_THOUSAND).toByte(),
            ((centiTotal / HUNDRED) % HUNDRED).toByte(),
            (centiTotal % HUNDRED).toByte()
        )
    }

    /** `[units, centi]` — the 2-byte amount encoding of the 0x91 totals and the 0x89/0x8C acks. */
    private fun encodeUnitsCenti(value: Double): ByteArray {
        val centiTotal = (value * HUNDRED).roundToInt().coerceAtLeast(0)
        return byteArrayOf((centiTotal / HUNDRED).toByte(), (centiTotal % HUNDRED).toByte())
    }

    /** Fixed-width ASCII, space-padded or truncated — the frame layout is positional. */
    private fun String.ascii(length: Int): ByteArray =
        padEnd(length).take(length).toByteArray(Charsets.US_ASCII)

    private fun ByteArray.u(index: Int): Int = this[index].toUByte().toInt()

    private fun hex(value: Byte): String = "%02X".format(value)

    @Suppress("unused")
    companion object {

        // Requests
        const val OP_SET_TIME: Byte = 0x11
        const val OP_SAFETY_CHECK: Byte = 0x12
        const val OP_BASAL_SET: Byte = 0x13
        const val OP_NOTICE_THRESHOLD: Byte = 0x15
        const val OP_INFUSION_THRESHOLD: Byte = 0x17
        const val OP_BUZZ_MODE: Byte = 0x18
        const val OP_NEEDLE_ACK: Byte = 0x19
        const val OP_NEEDLE_STATUS: Byte = 0x1A
        const val OP_THRESHOLD_SETUP: Byte = 0x1B
        const val OP_ADDITIONAL_PRIMING: Byte = 0x1D
        const val OP_BASAL_UPDATE: Byte = 0x21
        const val OP_TEMP_BASAL: Byte = 0x23
        const val OP_IMMEDIATE_BOLUS: Byte = 0x24
        const val OP_EXTEND_BOLUS: Byte = 0x25
        const val OP_PUMP_STOP: Byte = 0x26
        const val OP_PUMP_RESUME: Byte = 0x27
        const val OP_EXTEND_BOLUS_CANCEL: Byte = 0x29
        const val OP_BOLUS_CANCEL: Byte = 0x2C
        const val OP_TEMP_BASAL_CANCEL: Byte = 0x2D
        const val OP_INFUSION_INFO: Byte = 0x31
        const val OP_PATCH_INFO: Byte = 0x33
        const val OP_PATCH_DISCARD: Byte = 0x36
        const val OP_MAC_ADDRESS: Byte = 0x3B
        const val OP_ALARM_CLEAR: Byte = 0x47
        const val OP_ALERT_ALARM_SET: Byte = 0x48
        const val OP_APP_AUTH: Byte = 0x4B

        // Responses — hardcoded, never computed from the request.
        const val RESP_SET_TIME: Byte = 0x71
        const val RESP_SAFETY_CHECK: Byte = 0x72
        const val RESP_BASAL_SET: Byte = 0x73
        const val RESP_NOTICE_THRESHOLD: Byte = 0x75
        const val RESP_INFUSION_THRESHOLD: Byte = 0x77
        const val RESP_BUZZ_MODE: Byte = 0x78
        const val RESP_NEEDLE_STATUS: Byte = 0x79   // 0x1A -> 0x79, swapped with the ack below
        const val RESP_NEEDLE_ACK: Byte = 0x7A      // 0x19 -> 0x7A
        const val RESP_THRESHOLD_SETUP: Byte = 0x7B
        const val RESP_ADDITIONAL_PRIMING: Byte = 0x7D
        val RESP_BASAL_UPDATE: Byte = 0x81.toByte()
        val RESP_TEMP_BASAL: Byte = 0x83.toByte()
        val RESP_IMMEDIATE_BOLUS: Byte = 0x84.toByte()
        val RESP_EXTEND_BOLUS: Byte = 0x85.toByte()
        val RESP_PUMP_STOP: Byte = 0x86.toByte()
        val RESP_PUMP_RESUME: Byte = 0x87.toByte()
        val RESP_EXTEND_BOLUS_CANCEL: Byte = 0x89.toByte()
        val RESP_BOLUS_CANCEL: Byte = 0x8C.toByte()
        val RESP_TEMP_BASAL_CANCEL: Byte = 0x8D.toByte()
        val RESP_INFUSION_INFO: Byte = 0x91.toByte()
        val RESP_PATCH_INFO_RPT1: Byte = 0x93.toByte()
        val RESP_PATCH_INFO_RPT2: Byte = 0x94.toByte()
        val RESP_PATCH_DISCARD: Byte = 0x96.toByte()
        val RESP_MAC_ADDRESS: Byte = 0x9B.toByte()
        val RESP_ALARM_CLEAR: Byte = 0xA7.toByte()
        val RESP_ALERT_ALARM_SET: Byte = 0xA8.toByte()
        val RESP_APP_AUTH: Byte = 0xBB.toByte()     // 0x4B -> 0xBB, not 0xAB and not 0xBA

        /** Result byte of a 0x72 frame that means "still working" — see `SafetyCheckCommand`. */
        const val SAFETY_PROGRESS_RESULT = 4
        const val AUTH_FAILED = 1

        const val PUMP_STATE_READY = 0
        const val PUMP_STATE_RUNNING = 2

        const val MODE_BASAL = 1
        const val MODE_TEMP_BASAL = 2
        const val MODE_IMME_BOLUS = 3
        const val MODE_EXTEND_BOLUS = 5

        const val FLAG_ON: Byte = 0x01
        const val FLAG_MAX_VOLUME: Byte = 0x01
        const val NEEDLE_FLAG_SUCCESS: Byte = 0x00
        const val NOTICE_TYPE_LOW_INSULIN = 0
        const val NOTICE_TYPE_EXPIRY = 1

        const val SET_TIME_LENGTH = 11
        const val THRESHOLD_SETUP_LENGTH = 8
        const val INFUSION_INFO_LENGTH = 20
        const val SERIAL_LENGTH = 13
        const val FIRMWARE_LENGTH = 4
        const val MODEL_LENGTH = 5
        const val RPT2_UNUSED_LENGTH = 5
        const val BASAL_SEGMENT_START = 2
        const val BASAL_FINAL_SEQ = 2

        const val HUNDRED = 100
        const val TEN_THOUSAND = 10_000
        const val CENTI = 100.0
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60

        /** Rough delivery rate used to fabricate the 0x84 expected-duration bytes. */
        const val SECONDS_PER_UNIT = 20

        /** Byte 2 of the 0x85 ack carries whole minutes, so the wire cannot report beyond this. */
        const val MAX_REPORTABLE_MINUTES = 255
    }
}
