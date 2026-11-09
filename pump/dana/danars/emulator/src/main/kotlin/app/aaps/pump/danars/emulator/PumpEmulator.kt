package app.aaps.pump.danars.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.emulator.HistoryEvent
import app.aaps.pump.dana.emulator.ReviewRecordCodes
import app.aaps.pump.danars.encryption.BleEncryption
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Instant

/**
 * Emulates Dana RS pump command processing.
 *
 * Receives decrypted command (opcode + params), processes it against [PumpState],
 * and returns response bytes (DATA portion only, starting at DATA_START).
 */
class PumpEmulator(val state: PumpState = PumpState()) {

    /**
     * Callback for sending spontaneous pump-to-app messages.
     * Used for bolus delivery notifications and multi-response history events.
     * Set by EmulatorBleTransport.
     *
     * @param link the link the message belongs to, see [currentLink], or null for the link that is up now
     * @param type packet type (TYPE_NOTIFY for notifications, TYPE_RESPONSE for follow-up responses)
     * @param opCode the opcode
     * @param data the data bytes
     */
    var onSpontaneousMessage: ((link: Int?, type: Int, opCode: Int, data: ByteArray) -> Unit)? = null

    /** The link the command in [processCommand] came on. Set by the transport. */
    @Volatile var currentLink: Int = 0

    /** How long a bolus takes. Only a test makes it shorter, see `PumpEmulatorControl.speed`. */
    @Volatile var speed: EmulatorSpeed = EmulatorSpeed.REAL

    /** Delay between history events in [processApsHistoryEvents]. Tests can set to 0. */
    var historyEventDelayMs: Long = 10L

    // Track background notification threads (bolus delivery, history events) so tests can
    // await/cancel them and avoid touching Mockito mocks after inline mocks are cleared.
    private val pendingThreads = CopyOnWriteArrayList<Thread>()

    /**
     * Wait for all pending async notification threads to finish.
     * Call this in tests before teardown to avoid Mockito mock-cleared races.
     */
    fun awaitPendingCallbacks(timeoutMs: Long = 2000) {
        pendingThreads.toList().forEach { it.join(timeoutMs) }
        pendingThreads.removeAll { !it.isAlive }
    }

    /** Interrupt and join all pending notification threads (used on disconnect/teardown). */
    fun cancelPendingWork(timeoutMs: Long = 500) {
        val threads = pendingThreads.toList()
        pendingThreads.clear()
        threads.forEach { it.interrupt() }
        threads.forEach { runCatching { it.join(timeoutMs) } }
    }

    private fun launchTracked(block: () -> Unit) {
        val thread = Thread {
            try {
                block()
            } catch (_: InterruptedException) {
                // cancelled during teardown - exit quietly
            } finally {
                pendingThreads.remove(Thread.currentThread())
            }
        }
        pendingThreads.add(thread)
        thread.start()
    }

    init {
        // A pump read back after an app kill goes on with its bolus, as a real one does.
        state.runningBolus?.let { runBolus(it) }
    }

    /** All the pump holds, with the bolus it gives, for a test that kills the app. Read back with [restore]. */
    fun saveState(): ByteArray = synchronized(state) {
        ByteArrayOutputStream().also { bytes -> ObjectOutputStream(bytes).use { it.writeObject(state) } }.toByteArray()
    }

    /**
     * Process a command and return the response data bytes.
     * The response format matches what DanaRSPacket.handleMessage() expects:
     * bytes at positions starting from DATA_START (offset 2 in the full decrypted buffer).
     *
     * @param opCode the command opcode
     * @param params the request parameters (may be empty)
     * @return response data bytes (DATA portion only)
     */
    fun processCommand(opCode: Int, params: ByteArray): ByteArray {
        return when (opCode) {
            // General
            BleEncryption.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION               -> processKeepConnection()
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION   -> processGetShippingInformation()
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK             -> processGetPumpCheck()
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION -> processInitialScreenInformation()
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE    -> processSetHistoryUploadMode(params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BOLUS                      -> processReviewHistory(opCode, ReviewRecordCodes.BOLUS, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__DAILY                      -> processReviewHistory(opCode, ReviewRecordCodes.DAILY, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__PRIME                      -> processReviewHistory(opCode, ReviewRecordCodes.PRIME, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__REFILL                     -> processReviewHistory(opCode, ReviewRecordCodes.REFILL, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE              -> processReviewHistory(opCode, ReviewRecordCodes.GLUCOSE, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE               -> processReviewHistory(opCode, ReviewRecordCodes.CARBO, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__TEMPORARY                  -> processReviewHistory(opCode, ReviewRecordCodes.TEMP_BASAL, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__SUSPEND                    -> processReviewHistory(opCode, ReviewRecordCodes.SUSPEND, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__ALARM                      -> processReviewHistory(opCode, ReviewRecordCodes.ALARM, params)
            BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BASAL                      -> processReviewHistory(opCode, ReviewRecordCodes.BASAL_HOUR, params)

            // Basal
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER          -> processGetProfileNumber()
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE              -> processGetBasalRate()
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL         -> processSetTemporaryBasal(params)
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL      -> processCancelTemporaryBasal()
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL     -> processApsSetTemporaryBasal(params)
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE      -> processSetProfileBasalRate(params)
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER          -> processSetProfileNumber(params)

            // Bolus
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION  -> processGetStepBolusInformation()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION            -> processGetBolusOption()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION -> processGetCalculationInformation()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY            -> processGetCIRCFArray()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_24_CIR_CF_ARRAY         -> processGet24CIRCFArray()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START        -> processSetStepBolusStart(params)
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP         -> processSetStepBolusStop()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS          -> processSetExtendedBolus(params)
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL   -> processSetExtendedBolusCancel()
            BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_24_CIR_CF_ARRAY         -> processSet24CIRCFArray(params)

            // Options
            BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME              -> processGetPumpTime()
            BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_UTC_AND_TIME_ZONE -> processGetPumpUTCAndTimeZone()
            BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION            -> processGetUserOption()
            BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME              -> processSetPumpTime(params)
            BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_UTC_AND_TIME_ZONE -> processSetPumpUTCAndTimeZone(params)
            BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION            -> processSetUserOption(params)

            // APS
            BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS                -> processApsHistoryEvents(params)
            BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY             -> processApsSetEventHistory(params)

            else                                                                  -> byteArrayOf(0x00) // OK
        }
    }

    // --- General ---

    private fun processKeepConnection(): ByteArray = byteArrayOf(0x00) // OK

    private fun processGetShippingInformation(): ByteArray {
        val result = ByteArray(18)
        val serialBytes = state.serialNumber.toByteArray(Charsets.UTF_8)
        System.arraycopy(serialBytes, 0, result, 0, minOf(serialBytes.size, 10))
        val countryBytes = state.shippingCountry.toByteArray(Charsets.US_ASCII)
        System.arraycopy(countryBytes, 0, result, 10, minOf(countryBytes.size, 3))
        result[13] = (state.shippingDate.first - 2000).toByte()
        result[14] = state.shippingDate.second.toByte()
        result[15] = state.shippingDate.third.toByte()
        return result
    }

    private fun processGetPumpCheck(): ByteArray {
        return byteArrayOf(
            state.hwModel.toByte(),
            state.protocol.toByte(),
            state.productCode.toByte()
        )
    }

    private fun processInitialScreenInformation(): ByteArray {
        val result = ByteArray(17)
        var status = 0
        if (state.isSuspended) status = status or 0x01
        if (state.isTempBasalRunning) status = status or 0x10
        if (state.isExtendedBolusRunning) status = status or 0x04
        if (state.isDualBolusRunning) status = status or 0x08
        result[0] = status.toByte()
        putIntToArray(result, 1, (state.dailyTotalUnits * 100).toInt())
        putIntToArray(result, 3, (state.maxDailyTotalUnits * 100).toInt())
        putIntToArray(result, 5, (state.reservoirRemainingUnits * 100).toInt())
        putIntToArray(result, 7, (state.currentBasal * 100).toInt())
        result[9] = state.tempBasalPercent.toByte()
        result[10] = state.batteryRemaining.toByte()
        putIntToArray(result, 11, (state.extendedBolusAmount * 100).toInt())
        putIntToArray(result, 13, (state.iob * 100).toInt())
        result[15] = state.errorState.toByte()
        return result
    }

    private fun processSetHistoryUploadMode(params: ByteArray): ByteArray = byteArrayOf(0x00) // OK

    // --- Basal ---

    private fun processGetProfileNumber(): ByteArray =
        byteArrayOf(state.activeProfileNumber.toByte())

    private fun processGetBasalRate(): ByteArray {
        val result = ByteArray(51)
        putIntToArray(result, 0, (state.maxBasal * 100).toInt())
        result[2] = (state.basalStep * 100).toInt().toByte()
        val profile = state.basalProfiles[state.activeProfileNumber]
        for (i in 0 until 24) {
            putIntToArray(result, 3 + i * 2, (profile[i] * 100).toInt())
        }
        return result
    }

    private fun processSetTemporaryBasal(params: ByteArray): ByteArray {
        if (state.rejectTempBasal) return byteArrayOf(0x01) // refused
        if (params.size >= 2) {
            val percent = params[0].toInt() and 0xFF
            val durationHours = params[1].toInt() and 0xFF
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = durationHours * 60
            state.tempBasalStartTime = state.pumpTimeMillis
            addHistoryEvent(DanaPump.HistoryEntry.TEMP_START.value, state.tempBasalStartTime, percent, durationHours * 60)
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processCancelTemporaryBasal(): ByteArray {
        if (state.isTempBasalRunning) {
            addHistoryEvent(DanaPump.HistoryEntry.TEMP_STOP.value, state.pumpTimeMillis, 0, 0)
        }
        state.isTempBasalRunning = false
        state.tempBasalPercent = 0
        state.tempBasalDurationMinutes = 0
        return byteArrayOf(0x00) // OK
    }

    private fun processApsSetTemporaryBasal(params: ByteArray): ByteArray {
        if (state.rejectTempBasal) return byteArrayOf(0x01) // refused
        if (params.size >= 3) {
            val percent = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
            val durationMinutes = if (params[2].toInt() and 0xFF == 150) 15 else 30
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = durationMinutes
            state.tempBasalStartTime = state.pumpTimeMillis
            addHistoryEvent(DanaPump.HistoryEntry.TEMP_START.value, state.tempBasalStartTime, percent, durationMinutes)
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetProfileBasalRate(params: ByteArray): ByteArray {
        if (params.size >= 49) {
            val profileNum = params[0].toInt() and 0xFF
            if (profileNum in 0..3) {
                for (i in 0 until 24) {
                    val value = ((params[1 + i * 2].toInt() and 0xFF) or ((params[2 + i * 2].toInt() and 0xFF) shl 8))
                    state.basalProfiles[profileNum][i] = value / 100.0
                }
                updateCurrentBasal()
            }
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetProfileNumber(params: ByteArray): ByteArray {
        if (params.isNotEmpty()) {
            state.activeProfileNumber = params[0].toInt() and 0xFF
            updateCurrentBasal()
        }
        return byteArrayOf(0x00) // OK
    }

    /**
     * The pump shows the rate of this hour in its active profile as its current basal. The driver takes that as the
     * base rate, and a temp basal of the same rate is no temp basal for it. It is set when the profile changes, not
     * each hour: a test may set it by hand.
     */
    private fun updateCurrentBasal() {
        val profile = state.basalProfiles.getOrNull(state.activeProfileNumber) ?: return
        val hour = Instant.fromEpochMilliseconds(state.pumpTimeMillis).toLocalDateTime(TimeZone.currentSystemDefault()).hour
        state.currentBasal = profile[hour]
    }

    // --- Bolus ---

    private fun processGetStepBolusInformation(): ByteArray {
        val result = ByteArray(11)
        result[0] = 0 // error code OK
        result[1] = 0 // bolus type
        putIntToArray(result, 2, 0) // initial bolus
        // Report last bolus hour:minute in UTC (for Dana-i/BLE5) or local time
        val tz = if (state.usingUTC) TimeZone.UTC else TimeZone.currentSystemDefault()
        val ldt = Instant.fromEpochMilliseconds(state.lastBolusTime).toLocalDateTime(tz)
        result[4] = ldt.hour.toByte()
        result[5] = ldt.minute.toByte()
        putIntToArray(result, 6, (state.lastBolusAmount * 100).toInt())
        putIntToArray(result, 8, (state.maxBolus * 100).toInt())
        result[10] = (state.bolusStep * 100).toInt().toByte()
        return result
    }

    private fun processGetBolusOption(): ByteArray {
        val result = ByteArray(20)
        result[0] = if (state.isExtendedEnabled) 1 else 0
        // rest are missed bolus config, leave as 0
        return result
    }

    private fun processGetCalculationInformation(): ByteArray {
        val result = ByteArray(14)
        result[0] = 0 // error code OK
        putIntToArray(result, 1, state.currentBG)
        putIntToArray(result, 3, state.currentCarb)
        putIntToArray(result, 5, state.currentTarget)
        putIntToArray(result, 7, state.currentCIR)
        putIntToArray(result, 9, state.currentCF)
        putIntToArray(result, 11, (state.iob * 100).toInt())
        result[13] = state.units.toByte()
        return result
    }

    private fun processGetCIRCFArray(): ByteArray {
        val result = ByteArray(49)
        result[0] = state.language.toByte()
        result[1] = state.units.toByte()
        // 7 CIR values (2 bytes each)
        for (i in 0 until 7) {
            putIntToArray(result, 2 + i * 2, state.cirValues[i])
        }
        // 7 CF values (2 bytes each, starting at offset 16)
        for (i in 0 until 7) {
            val cfValue = if (state.units == 1) (state.cfValues[i] * 100) else state.cfValues[i]
            putIntToArray(result, 16 + i * 2, cfValue)
        }
        return result
    }

    private fun processGet24CIRCFArray(): ByteArray {
        val result = ByteArray(97)
        result[0] = state.units.toByte()
        // 24 CIR values (2 bytes each)
        for (i in 0 until 24) {
            putIntToArray(result, 1 + i * 2, state.cir24Values[i])
        }
        // 24 CF values (2 bytes each)
        for (i in 0 until 24) {
            val cfValue = if (state.units == 1) (state.cf24Values[i] * 100) else state.cf24Values[i]
            putIntToArray(result, 49 + i * 2, cfValue)
        }
        return result
    }

    /**
     * Seen on a real Dana-i (2026-11-09): the start is logged at once as STEP_BOLUS_COMMAND with the amount asked for,
     * the bolus is given in 0.05 U steps with a progress message after each, and it is logged as BOLUS only at its end,
     * with what it gave. Params: amount in 0.01 U (2 bytes), speed (0, 1, 2 = 12, 30, 60 s/U), 1 for a bolus of the
     * algorithm.
     */
    private fun processSetStepBolusStart(params: ByteArray): ByteArray {
        if (params.size < 3) return byteArrayOf(0x00)
        val amountHundredths = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
        val secondsPerUnit = when (params[2].toInt() and 0xFF) {
            1    -> 30
            2    -> 60
            else -> 12
        }
        val bolus = synchronized(state) {
            // UNVERIFIED: the code a real pump answers a second start with while it gives a bolus.
            if (state.runningBolus != null) return byteArrayOf(ERROR_BOLUS_RUNNING)
            val msPerStep =
                if (state.bolusDeliveryIntervalMs <= 0) 0L
                else secondsPerUnit * 1000L * RunningBolus.STEP_HUNDREDTHS / 100 / if (speed == EmulatorSpeed.FAST) FAST_DIVISOR else 1
            // A Dana-i writes this record, older models do not.
            if (state.hwModel == HW_MODEL_DANA_I)
                addHistoryEvent(DanaPump.HistoryEntry.STEP_BOLUS_COMMAND.value, state.pumpTimeMillis, amountHundredths, if (params.size > 3) params[3].toInt() and 0xFF else 0)
            RunningBolus(System.currentTimeMillis(), amountHundredths, msPerStep, currentLink).also { state.runningBolus = it }
        }
        runBolus(bolus)
        return byteArrayOf(0x00) // OK (error code 0 = no error)
    }

    /** Seen on a real Dana-i: the step that runs is given, then the bolus ends with what it gave. A stop with no bolus is answered OK too. */
    private fun processSetStepBolusStop(): ByteArray {
        synchronized(state) {
            state.runningBolus?.let { bolus ->
                if (bolus.stopAfterSteps == null) bolus.stopAfterSteps = minOf(bolus.totalSteps, bolus.stepsDue(System.currentTimeMillis()) + 1)
            }
        }
        return byteArrayOf(0x00) // OK
    }

    /** Gives [bolus] step by step until it ends. The progress goes only to the link the start came on. */
    private fun runBolus(bolus: RunningBolus) = launchTracked {
        @Suppress("SleepInsteadOfDelay")
        while (true) {
            val (messages, ended) = synchronized(state) { giveDueSteps(bolus, System.currentTimeMillis()) }
            messages.forEach { (opCode, data) -> onSpontaneousMessage?.invoke(bolus.link, BleEncryption.DANAR_PACKET__TYPE_NOTIFY, opCode, data) }
            if (ended || Thread.currentThread().isInterrupted) return@launchTracked
            val nextStepAt = bolus.startedAt + (bolus.stepsGiven + 1) * bolus.msPerStep
            Thread.sleep((nextStepAt - System.currentTimeMillis()).coerceIn(1L, bolus.msPerStep.coerceAtLeast(1L)))
        }
    }

    /**
     * Under the lock of [state]. Gives the steps of [bolus] that are due at [now], and ends it when it is done.
     * @return the messages to send, and whether the bolus ended
     */
    private fun giveDueSteps(bolus: RunningBolus, now: Long): Pair<List<Pair<Int, ByteArray>>, Boolean> {
        if (state.runningBolus !== bolus) return emptyList<Pair<Int, ByteArray>>() to true
        val messages = ArrayList<Pair<Int, ByteArray>>()
        val due = bolus.stepsDue(now)
        while (bolus.stepsGiven < due) {
            val before = bolus.givenHundredths(bolus.stepsGiven)
            bolus.stepsGiven++
            val given = bolus.givenHundredths(bolus.stepsGiven)
            state.reservoirRemainingUnits -= (given - before) / 100.0
            // Seen on a real Dana-i: after a stop the last step sends no progress, only the end.
            if (bolus.stopAfterSteps == null)
                messages += BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY to hundredthsBytes(given)
        }
        if (!bolus.ended(bolus.stepsGiven)) return messages to false
        val given = bolus.givenHundredths(bolus.stepsGiven)
        messages += BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE to hundredthsBytes(given)
        addHistoryEvent(DanaPump.HistoryEntry.BOLUS.value, state.pumpTimeMillis, given, 0)
        state.givenBoluses += given / 100.0
        state.lastBolusAmount = given / 100.0
        state.lastBolusTime = bolus.startedAt + state.clockOffsetMs
        state.dailyTotalUnits += given / 100.0
        state.runningBolus = null
        return messages to true
    }

    private fun hundredthsBytes(value: Int): ByteArray = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun processSetExtendedBolus(params: ByteArray): ByteArray {
        if (params.size >= 3) {
            val amountHundredths = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
            val durationHalfHours = params[2].toInt() and 0xFF
            state.isExtendedBolusRunning = true
            state.extendedBolusAmount = amountHundredths / 100.0
            state.extendedBolusDurationHalfHours = durationHalfHours
            addHistoryEvent(DanaPump.HistoryEntry.EXTENDED_START.value, state.pumpTimeMillis, amountHundredths, durationHalfHours * 30)
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetExtendedBolusCancel(): ByteArray {
        if (state.isExtendedBolusRunning) {
            addHistoryEvent(DanaPump.HistoryEntry.EXTENDED_STOP.value, state.pumpTimeMillis, 0, 0)
        }
        state.isExtendedBolusRunning = false
        state.extendedBolusAmount = 0.0
        state.extendedBolusDurationHalfHours = 0
        return byteArrayOf(0x00) // OK
    }

    private fun processSet24CIRCFArray(params: ByteArray): ByteArray {
        if (params.size >= 96) {
            for (i in 0 until 24) {
                state.cir24Values[i] = (params[i * 2].toInt() and 0xFF) or ((params[i * 2 + 1].toInt() and 0xFF) shl 8)
            }
            for (i in 0 until 24) {
                state.cf24Values[i] = (params[48 + i * 2].toInt() and 0xFF) or ((params[48 + i * 2 + 1].toInt() and 0xFF) shl 8)
            }
        }
        return byteArrayOf(0x00) // OK
    }

    // --- Options ---

    private fun processGetPumpTime(): ByteArray {
        val ldt = Instant.fromEpochMilliseconds(state.pumpTimeMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return byteArrayOf(
            (ldt.year - 2000).toByte(),
            ldt.month.number.toByte(),
            ldt.day.toByte(),
            ldt.hour.toByte(),
            ldt.minute.toByte(),
            ldt.second.toByte()
        )
    }

    private fun processGetPumpUTCAndTimeZone(): ByteArray {
        val ldt = Instant.fromEpochMilliseconds(state.pumpTimeMillis)
            .toLocalDateTime(TimeZone.UTC)
        return byteArrayOf(
            (ldt.year - 2000).toByte(),
            ldt.month.number.toByte(),
            ldt.day.toByte(),
            ldt.hour.toByte(),
            ldt.minute.toByte(),
            ldt.second.toByte(),
            state.zoneOffset.toByte()
        )
    }

    private fun processGetUserOption(): ByteArray {
        val result = ByteArray(22)
        result[0] = state.timeDisplayType.toByte()
        result[1] = state.buttonScroll.toByte()
        result[2] = state.beepAndAlarm.toByte()
        result[3] = state.lcdOnTimeSec.toByte()
        result[4] = state.backlightOnTimeSec.toByte()
        result[5] = state.selectedLanguage.toByte()
        result[6] = state.units.toByte()
        result[7] = state.shutdownHour.toByte()
        result[8] = state.lowReservoirRate.toByte()
        putIntToArray(result, 9, state.cannulaVolume)
        putIntToArray(result, 11, state.refillAmount)
        // bytes 13-17: selectable language flags, leave as 0
        putIntToArray(result, 18, state.targetBG)
        return result
    }

    private fun processSetPumpTime(params: ByteArray): ByteArray {
        if (params.size >= 6) {
            val year = 2000 + (params[0].toInt() and 0xFF)
            val month = params[1].toInt() and 0xFF
            val day = params[2].toInt() and 0xFF
            val hour = params[3].toInt() and 0xFF
            val minute = params[4].toInt() and 0xFF
            val second = params[5].toInt() and 0xFF
            val ldt = LocalDateTime(year, month, day, hour, minute, second)
            val tz = TimeZone.currentSystemDefault()
            state.pumpTimeMillis = ldt.toInstant(tz).toEpochMilliseconds()
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetPumpUTCAndTimeZone(params: ByteArray): ByteArray {
        if (params.size >= 7) {
            val year = 2000 + (params[0].toInt() and 0xFF)
            val month = params[1].toInt() and 0xFF
            val day = params[2].toInt() and 0xFF
            val hour = params[3].toInt() and 0xFF
            val minute = params[4].toInt() and 0xFF
            val second = params[5].toInt() and 0xFF
            val ldt = LocalDateTime(year, month, day, hour, minute, second)
            state.pumpTimeMillis = ldt.toInstant(TimeZone.UTC).toEpochMilliseconds()
            state.zoneOffset = params[6].toInt()
            state.usingUTC = true
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetUserOption(params: ByteArray): ByteArray {
        if (params.size >= 13) {
            state.timeDisplayType = params[0].toInt() and 0xFF
            state.buttonScroll = params[1].toInt() and 0xFF
            state.beepAndAlarm = params[2].toInt() and 0xFF
            state.lcdOnTimeSec = params[3].toInt() and 0xFF
            state.backlightOnTimeSec = params[4].toInt() and 0xFF
            state.selectedLanguage = params[5].toInt() and 0xFF
            state.units = params[6].toInt() and 0xFF
            state.shutdownHour = params[7].toInt() and 0xFF
            state.lowReservoirRate = params[8].toInt() and 0xFF
            state.cannulaVolume = (params[9].toInt() and 0xFF) or ((params[10].toInt() and 0xFF) shl 8)
            state.refillAmount = (params[11].toInt() and 0xFF) or ((params[12].toInt() and 0xFF) shl 8)
            if (params.size >= 15) {
                state.targetBG = (params[13].toInt() and 0xFF) or ((params[14].toInt() and 0xFF) shl 8)
            }
        }
        return byteArrayOf(0x00) // OK
    }

    // --- APS ---

    private fun processApsHistoryEvents(params: ByteArray): ByteArray {
        val store = state.historyStore
        // DanaRSPacketAPSHistoryEvents writes the request in UTC only when the pump is usingUTC,
        // and buildHistoryEventData answers in the matching format — so read it back the same way.
        val fromMillis = store.parseFromTimestamp(params, usingUtc = state.usingUTC)
        val events = store.getEventsAfter(fromMillis)

        if (events.isEmpty()) return store.doneMarker

        // Return first event directly; send remaining + done via spontaneous messages
        val link = currentLink
        launchTracked {
            @Suppress("SleepInsteadOfDelay")
            for (i in 1 until events.size) {
                if (historyEventDelayMs > 0) Thread.sleep(historyEventDelayMs)
                if (Thread.currentThread().isInterrupted) return@launchTracked
                val data = buildHistoryEventData(events[i])
                onSpontaneousMessage?.invoke(
                    link,
                    BleEncryption.DANAR_PACKET__TYPE_RESPONSE,
                    BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS,
                    data
                )
            }
            Thread.sleep(10)
            if (Thread.currentThread().isInterrupted) return@launchTracked
            onSpontaneousMessage?.invoke(
                link,
                BleEncryption.DANAR_PACKET__TYPE_RESPONSE,
                BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS,
                store.doneMarker
            )
        }

        return buildHistoryEventData(events[0])
    }

    /**
     * Build a history event data payload in the format matching the pump's time mode.
     */
    private fun buildHistoryEventData(event: HistoryEvent): ByteArray {
        return if (state.usingUTC) {
            state.historyStore.buildEventDataUtc(event)
        } else {
            state.historyStore.buildEventData(event)
        }
    }

    private fun addHistoryEvent(code: Int, timestamp: Long, param1: Int, param2: Int) {
        state.historyStore.addEvent(code, timestamp, param1, param2)
    }

    private fun processApsSetEventHistory(@Suppress("unused") params: ByteArray): ByteArray = byteArrayOf(0x00) // OK

    // --- Review history (the per-type pump history screen) ---

    /**
     * Serve one type of review history: every record of [recordCode] newer than the request's
     * "from" timestamp, followed by the end marker.
     *
     * Same shape as [processApsHistoryEvents] — first record inline, the rest plus the terminator
     * as spontaneous messages under the same [opCode] — because the driver's wait loop is the same:
     * `DanaRSService.loadHistory` blocks until a reply's payload is short enough to read as the
     * end, so the marker must always be sent, including for an empty history.
     *
     * Filtered by [recordCode] rather than served wholesale: each `REVIEW__*` command asks for one
     * type, and a real pump answers with that type only. The driver would not notice — it
     * classifies on the record's own code, not the opcode it asked under — but a test asserting
     * "the bolus screen shows no alarms" should be able to trust this.
     */
    private fun processReviewHistory(opCode: Int, recordCode: Int, params: ByteArray): ByteArray {
        val store = state.reviewHistoryStore
        // Always local, unlike the APS event history: DanaRSPacketHistory builds its request from a
        // plain GregorianCalendar whatever the pump, and buildReviewRecordData answers in kind.
        val fromMillis = store.parseFromTimestamp(params, usingUtc = false)
        val records = store.getEventsAfter(fromMillis).filter { it.code == recordCode }

        if (records.isEmpty()) return store.reviewDoneMarker

        val link = currentLink
        launchTracked {
            @Suppress("SleepInsteadOfDelay")
            for (i in 1 until records.size) {
                if (historyEventDelayMs > 0) Thread.sleep(historyEventDelayMs)
                if (Thread.currentThread().isInterrupted) return@launchTracked
                onSpontaneousMessage?.invoke(
                    link,
                    BleEncryption.DANAR_PACKET__TYPE_RESPONSE,
                    opCode,
                    store.buildReviewRecordData(records[i])
                )
            }
            Thread.sleep(10)
            if (Thread.currentThread().isInterrupted) return@launchTracked
            onSpontaneousMessage?.invoke(
                link,
                BleEncryption.DANAR_PACKET__TYPE_RESPONSE,
                opCode,
                store.reviewDoneMarker
            )
        }

        return store.buildReviewRecordData(records[0])
    }

    // --- Helpers ---

    private fun putIntToArray(array: ByteArray, position: Int, value: Int) {
        array[position] = (value and 0xFF).toByte()
        if (position + 1 < array.size) {
            array[position + 1] = ((value shr 8) and 0xFF).toByte()
        }
    }

    companion object {

        /** The emulated pump of a test run, kept outside the graph, see [PumpEmulators]. */
        val KEY = PumpEmulators.Key<PumpEmulator>("danars")

        /**
         * The pump of [saveState], in a new process. The link of its bolus ended with the old process, and a link of
         * the new one can have the same number, so the bolus belongs to no link.
         */
        fun restore(saved: ByteArray): PumpEmulator {
            val state = ObjectInputStream(ByteArrayInputStream(saved)).use { it.readObject() } as PumpState
            state.runningBolus?.link = NO_LINK
            return PumpEmulator(state)
        }

        /** No link has this number. */
        private const val NO_LINK = -1

        /** The hardware model of a Dana-i. */
        private const val HW_MODEL_DANA_I = 0x09

        /** A bolus takes a quarter of its real time in the fast test runs. */
        private const val FAST_DIVISOR = 4

        private const val ERROR_BOLUS_RUNNING: Byte = 0x01
    }
}
