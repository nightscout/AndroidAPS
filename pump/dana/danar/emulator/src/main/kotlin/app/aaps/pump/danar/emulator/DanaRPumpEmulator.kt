package app.aaps.pump.danar.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import org.joda.time.DateTime
import kotlin.math.roundToInt

/**
 * Emulates DanaR pump command processing.
 * Receives raw command bytes (opcode + params) and returns response data bytes.
 *
 * Packet structure handled by [EmulatorRfcommTransport]:
 *   Request:  7E 7E [len] F1 [CMD_HI CMD_LO] [params...] [CRC16] 2E 2E
 *   Response: 7E 7E [len] F1 [CMD_HI CMD_LO] [data...]   [CRC16] 2E 2E
 */
class DanaRPumpEmulator(
    val state: DanaRPumpState = DanaRPumpState(),
    private val aapsLogger: AAPSLogger? = null
) {

    /**
     * Process a command and return response data bytes (without framing/CRC).
     * @param command 2-byte opcode (e.g., 0xF0F1)
     * @param params parameter bytes from the request
     * @return response data bytes
     */
    fun processCommand(command: Int, params: ByteArray): ByteArray {
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator processing command: ${String.format("%04X", command)}")

        return when (command) {
            // Initialization sequence
            0xF0F1                         -> handleCheckValue()
            0x0301                         -> handleInitConnStatusTime()
            0x0302                         -> handleInitConnStatusBolus()
            0x0303                         -> handleInitConnStatusBasic()
            0x0304                         -> handleInitConnStatusOption()

            // Status queries
            0x020B                         -> handleStatus()
            0x020A                         -> handleStatusBasic()
            0x0205                         -> handleStatusTempBasal()
            0x0207                         -> handleStatusBolusExtended()
            0x0204                         -> handleStatusProfile()

            // Settings queries
            0x3202                         -> handleSettingBasal()
            0x3203                         -> handleSettingMeal()
            0x3204                         -> handleSettingProfileRatios()
            0x3205                         -> handleSettingMaxValues()
            0x3206                         -> handleSettingBasalProfileAll()
            0x3207                         -> handleSettingShippingInfo()
            0x3209                         -> handleSettingGlucose()
            0x320A                         -> handleSettingPumpTime()
            0x320B                         -> handleSettingUserOptions()
            0x320C                         -> handleSettingActiveProfile()
            0x320D                         -> handleSettingProfileRatiosAll()

            // Bolus commands
            0x0102                         -> handleBolusStart(params)
            0x0104                         -> handleBolusStart(params) // with speed
            0x0101                         -> handleBolusStop()

            // Temp basal commands
            0x0401                         -> handleSetTempBasalStart(params)
            0x0403                         -> handleSetTempBasalStop()

            // Extended bolus commands
            0x0407                         -> handleSetExtendedBolusStart(params)
            0x0406                         -> handleSetExtendedBolusStop()

            // Set commands
            0x3302                         -> handleSetSingleBasalProfile(params)
            0x3306                         -> handleSetBasalProfile(params)
            0x330B                         -> handleSetUserOptions(params)
            0x330C                         -> handleSetActivateBasalProfile(params)
            0x330A                         -> handleSetTime(params)
            0x0402                         -> handleSetCarbsEntry()

            // History
            0x3001                         -> handlePCCommStart()
            0x3002                         -> handlePCCommStop()
            0x3101, 0x3102, 0x3104, 0x3105, 0x3106,
            0x3107, 0x3108, 0x3109, 0x310A -> handleHistoryRequest(command)

            0x31F1                         -> handleHistoryDone()
            0x41F1                         -> handleHistoryDone()
            0x41F2, 0x42F1, 0x42F2         -> handleHistoryRequest(command)

            // Rv2 APS commands
            0xE001                         -> handleApsStatus()
            0xE002                         -> handleApsSetTempBasal(params)
            0xE003                         -> handleApsHistoryEvents(params)
            0xE004                         -> handleApsSetHistoryEntry()

            // Error
            0x0601                         -> byteArrayOf(0x00)

            else                           -> {
                aapsLogger?.warn(LTag.PUMPEMULATOR, "Emulator: unhandled command ${String.format("%04X", command)}")
                byteArrayOf(0x00)
            }
        }
    }

    // --- Initialization ---

    private fun handleCheckValue(): ByteArray = byteArrayOf(
        state.hwModel.toByte(),
        state.protocol.toByte(),
        state.productCode.toByte()
    )

    private fun handleInitConnStatusTime(): ByteArray {
        val dt = DateTime(state.pumpTimeMillis)
        return when (state.variant) {
            DanaRVariant.DANA_R_KOREAN -> {
                // Korean: must be < 10 bytes payload (triggers Korean detection in app)
                byteArrayOf(
                    (dt.year - 2000).toByte(),
                    dt.monthOfYear.toByte(),
                    dt.dayOfMonth.toByte(),
                    dt.hourOfDay.toByte(),
                    dt.minuteOfHour.toByte(),
                    dt.secondOfMinute.toByte(),
                    0x01, 0x01, 0x01, 0x01 // 4 version codes (Korean expects these)
                )
            }

            else                       -> {
                // DanaR / v2: must be > 7 bytes payload
                byteArrayOf(
                    (dt.year - 2000).toByte(),
                    dt.monthOfYear.toByte(),
                    dt.dayOfMonth.toByte(),
                    dt.hourOfDay.toByte(),
                    dt.minuteOfHour.toByte(),
                    dt.secondOfMinute.toByte(),
                    0x01 // firmware version code
                )
            }
        }
    }

    private fun handleInitConnStatusBolus(): ByteArray {
        val bolusStepInt = (state.bolusStep * 100).toInt()
        val maxBolusInt = (state.maxBolus * 100).toInt()
        return when (state.variant) {
            DanaRVariant.DANA_R_KOREAN -> {
                // Korean: MsgInitConnStatusBolusK — 13+ bytes, byte[12] = deliveryStatus
                // This is the LAST init message for Korean — finishHandshaking() called here
                val result = ByteArray(13)
                result[0] = (if (state.isExtendedEnabled) 0x01 else 0x00).toByte()
                result[1] = bolusStepInt.toByte()
                result[2] = (maxBolusInt shr 8).toByte()
                result[3] = maxBolusInt.toByte()
                // bytes 4-11: bolus rate table
                result[12] = 0x08 // deliveryStatus (BASAL running)
                result
            }

            else                       -> {
                // DanaR / v2: standard layout, must be <= 12 bytes
                byteArrayOf(
                    (if (state.isExtendedEnabled) 0x01 else 0x00).toByte(),
                    bolusStepInt.toByte(),
                    (maxBolusInt shr 8).toByte(),
                    maxBolusInt.toByte(),
                    // 8 bytes bolus rate table (unused)
                    0, 0, 0, 0, 0, 0, 0, 0
                )
            }
        }
    }

    private fun handleInitConnStatusBasic(): ByteArray {
        return when (state.variant) {
            DanaRVariant.DANA_R_KOREAN -> {
                // Korean: MsgInitConnStatusBasicK — password at bytes 4-5
                val encryptedPassword = state.password xor 0x3463
                byteArrayOf(
                    if (state.isSuspended) 0x01 else 0x00,       // 0: pumpSuspended
                    0x01,                                          // 1: isUtilityEnable
                    0x00,                                          // 2: isEasyModeEnabled (must be 0)
                    0x00,                                          // 3: easyUIMode
                    (encryptedPassword shr 8 and 0xFF).toByte(),   // 4-5: password XOR 0x3463
                    (encryptedPassword and 0xFF).toByte()
                )
            }

            else                       -> {
                // DanaR / v2: standard 22-byte layout
                val dailyInt = (state.dailyTotalUnits * 750).toInt()
                val maxDailyInt = (state.maxDailyTotalUnits * 100).toInt()
                val reservoirInt = (state.reservoirRemainingUnits * 750).toInt()
                val currentBasalInt = (state.currentBasal * 100).toInt()

                byteArrayOf(
                    if (state.isSuspended) 0x01 else 0x00,           // 0: pumpSuspended
                    if (state.calculatorEnabled) 0x01 else 0x00,     // 1: calculatorEnabled
                    (dailyInt and 0xFF).toByte(),                     // 2-4: dailyTotalUnits (3 bytes LE)
                    (dailyInt shr 8 and 0xFF).toByte(),
                    (dailyInt shr 16 and 0xFF).toByte(),
                    (maxDailyInt shr 8).toByte(),                     // 5-6: maxDailyTotalUnits
                    maxDailyInt.toByte(),
                    (reservoirInt and 0xFF).toByte(),                 // 7-9: reservoir (3 bytes LE)
                    (reservoirInt shr 8 and 0xFF).toByte(),
                    (reservoirInt shr 16 and 0xFF).toByte(),
                    if (state.bolusBlocked) 0x01 else 0x00,           // 10: bolusBlocked
                    (currentBasalInt shr 8).toByte(),                 // 11-12: currentBasal
                    currentBasalInt.toByte(),
                    state.tempBasalPercent.toByte(),                   // 13: tempBasalPercent
                    if (state.isExtendedBolusRunning) 0x01 else 0x00, // 14: isExtendedInProgress
                    if (state.isTempBasalRunning) 0x01 else 0x00,     // 15: isTempBasalInProgress
                    0x00,                                              // 16: statusBasalUDOption
                    if (state.isDualBolusRunning) 0x01 else 0x00,     // 17: isDualBolusInProgress
                    0x00, 0x00,                                        // 18-19: extendedBolusRate
                    state.batteryRemaining.toByte(),                   // 20: batteryRemaining
                    0x08                                               // 21: delivery flags (BASAL)
                )
            }
        }
    }

    private fun handleInitConnStatusOption(): ByteArray {
        // Only for DanaR and DanaR v2 (not Korean)
        val encryptedPassword = state.password xor 0x3463
        return byteArrayOf(
            if (state.timeDisplayType24) 0x00 else 0x01,    // 0: time format
            if (state.buttonScroll) 0x01 else 0x00,         // 1: button scroll
            state.beepAndAlarm.toByte(),                     // 2: sound/vibration
            state.glucoseUnit.toByte(),                      // 3: glucose unit
            state.lcdOnTimeSec.toByte(),                     // 4: lcd timeout
            state.backlightOnTimeSec.toByte(),               // 5: backlight timeout
            0x00,                                            // 6: language
            state.lowReservoirRate.toByte(),                 // 7: low reservoir
            0x00,                                            // 8: reserved
            (encryptedPassword shr 8 and 0xFF).toByte(),     // 9-10: password XOR 0x3463
            (encryptedPassword and 0xFF).toByte()
        )
    }

    // --- Status queries ---

    private fun handleStatus(): ByteArray {
        val dailyInt = (state.dailyTotalUnits * 750).toInt()
        val lastBolusInt = (state.lastBolusAmount * 100).toInt()
        val iobInt = (state.iob * 100).toInt()

        val result = ByteArray(17)
        // 0-2: daily total (3 bytes LE)
        result[0] = (dailyInt and 0xFF).toByte()
        result[1] = (dailyInt shr 8 and 0xFF).toByte()
        result[2] = (dailyInt shr 16 and 0xFF).toByte()
        // 3: isExtendedInProgress
        result[3] = if (state.isExtendedBolusRunning) 0x01 else 0x00
        // 4-5: extended bolus minutes remaining
        result[4] = 0; result[5] = 0
        // 6-7: extended bolus amount
        result[6] = 0; result[7] = 0
        // 8-12: last bolus time (Y,M,D,H,M)
        if (state.lastBolusTime > 0) {
            val dt = DateTime(state.lastBolusTime)
            result[8] = (dt.year - 2000).toByte()
            result[9] = dt.monthOfYear.toByte()
            result[10] = dt.dayOfMonth.toByte()
            result[11] = dt.hourOfDay.toByte()
            result[12] = dt.minuteOfHour.toByte()
        }
        // 13-14: last bolus amount
        result[13] = (lastBolusInt shr 8).toByte()
        result[14] = lastBolusInt.toByte()
        // 15-16: IOB
        result[15] = (iobInt shr 8).toByte()
        result[16] = iobInt.toByte()
        return result
    }

    private fun handleStatusBasic(): ByteArray {
        val dailyInt = (state.dailyTotalUnits * 750).toInt()
        val maxDailyInt = (state.maxDailyTotalUnits * 100).toInt()
        val reservoirInt = (state.reservoirRemainingUnits * 750).toInt()
        val currentBasalInt = (state.currentBasal * 100).toInt()

        return when (state.variant) {
            DanaRVariant.DANA_R_KOREAN -> {
                // Korean layout: [currentBasal(2)][battery(1)][reservoir(3)][daily(3)][maxDaily(2)]
                ByteArray(11).also { b ->
                    b[0] = (currentBasalInt shr 8).toByte()
                    b[1] = (currentBasalInt and 0xFF).toByte()
                    b[2] = state.batteryRemaining.toByte()
                    b[3] = (reservoirInt and 0xFF).toByte()
                    b[4] = (reservoirInt shr 8 and 0xFF).toByte()
                    b[5] = (reservoirInt shr 16 and 0xFF).toByte()
                    b[6] = (dailyInt and 0xFF).toByte()
                    b[7] = (dailyInt shr 8 and 0xFF).toByte()
                    b[8] = (dailyInt shr 16 and 0xFF).toByte()
                    b[9] = (maxDailyInt shr 8).toByte()
                    b[10] = (maxDailyInt and 0xFF).toByte()
                }
            }

            else                       -> {
                // DanaR / v2 layout
                ByteArray(21).also { b ->
                    b[0] = if (state.isSuspended) 0x01 else 0x00
                    b[1] = if (state.calculatorEnabled) 0x01 else 0x00
                    b[2] = (dailyInt and 0xFF).toByte()
                    b[3] = (dailyInt shr 8 and 0xFF).toByte()
                    b[4] = (dailyInt shr 16 and 0xFF).toByte()
                    b[5] = (maxDailyInt shr 8).toByte()
                    b[6] = maxDailyInt.toByte()
                    b[7] = (reservoirInt and 0xFF).toByte()
                    b[8] = (reservoirInt shr 8 and 0xFF).toByte()
                    b[9] = (reservoirInt shr 16 and 0xFF).toByte()
                    b[10] = if (state.bolusBlocked) 0x01 else 0x00
                    b[11] = (currentBasalInt shr 8).toByte()
                    b[12] = currentBasalInt.toByte()
                    b[13] = state.tempBasalPercent.toByte()
                    b[14] = if (state.isExtendedBolusRunning) 0x01 else 0x00
                    b[15] = if (state.isTempBasalRunning) 0x01 else 0x00
                    // 16-19: padding
                    b[20] = state.batteryRemaining.toByte()
                }
            }
        }
    }

    /**
     * MsgStatusTempBasal. An APS temp basal (15 or 30 minutes) has flag 0x02 and the duration code 150 or 160 instead of
     * hours: UNVERIFIED on a DanaR, read from what the driver's parser looks for.
     */
    private fun handleStatusTempBasal(): ByteArray {
        val running = state.isTempBasalRunning
        val elapsed = if (running) ((state.pumpTimeMillis - state.tempBasalStartTime) / 1000).toInt() else 0
        val aps = running && state.tempBasalDurationMinutes < 60
        val durationCode = when {
            !aps                                -> state.tempBasalDurationMinutes / 60
            state.tempBasalDurationMinutes == 15 -> APS_PARAM_15_MIN
            else                                -> APS_PARAM_30_MIN
        }
        val flags = (if (running) 0x01 else 0x00) or (if (aps) 0x02 else 0x00)

        return byteArrayOf(
            flags.toByte(),                                   // 0: status flags
            state.tempBasalPercent.toByte(),                  // 1: percent
            durationCode.toByte(),                            // 2: duration code (hours)
            (elapsed and 0xFF).toByte(),                      // 3-5: running seconds (3 bytes LE)
            (elapsed shr 8 and 0xFF).toByte(),
            (elapsed shr 16 and 0xFF).toByte()
        )
    }

    private fun handleStatusBolusExtended(): ByteArray {
        val amountInt = (state.extendedBolusAmount * 100).toInt()
        val elapsed = if (state.isExtendedBolusRunning) 0 else 0 // simplified

        return byteArrayOf(
            if (state.isExtendedBolusRunning) 0x01 else 0x00, // 0: isExtendedInProgress
            state.extendedBolusDurationHalfHours.toByte(),     // 1: half hours
            (amountInt shr 8).toByte(),                        // 2-3: amount
            amountInt.toByte(),
            (elapsed and 0xFF).toByte(),                       // 4-6: seconds elapsed (3 bytes LE)
            (elapsed shr 8 and 0xFF).toByte(),
            (elapsed shr 16 and 0xFF).toByte()
        )
    }

    private fun handleStatusProfile(): ByteArray = ByteArray(4) // simplified

    // --- Settings ---

    private fun handleSettingBasal(): ByteArray {
        val profile = state.basalProfiles[state.activeProfile]
        val result = ByteArray(48) // 24 * 2 bytes
        for (i in 0 until 24) {
            val rateInt = (profile[i] * 100).toInt()
            result[i * 2] = (rateInt shr 8).toByte()
            result[i * 2 + 1] = rateInt.toByte()
        }
        return result
    }

    private fun handleSettingMeal(): ByteArray {
        val data = ByteArray(12)
        data[0] = (state.basalStep * 100).toInt().toByte()  // basalStep (1 = 0.01 U/h)
        data[1] = (state.bolusStep * 100).toInt().toByte()  // bolusStep (10 = 0.10 U)
        data[2] = 0x01                                       // bolus enabled
        // data[5] = isConfigUD (0 = U/h mode)
        return data
    }

    private fun handleSettingProfileRatios(): ByteArray = ByteArray(12) // simplified
    private fun handleSettingProfileRatiosAll(): ByteArray = ByteArray(48) // simplified
    private fun handleSettingGlucose(): ByteArray = ByteArray(4) // simplified

    private fun handleSettingBasalProfileAll(): ByteArray {
        val result = ByteArray(48 * 4) // 4 profiles * 24 hours * 2 bytes
        for (p in 0 until 4) {
            for (h in 0 until 24) {
                val rateInt = (state.basalProfiles[p][h] * 100).toInt()
                val offset = p * 48 + h * 2
                result[offset] = (rateInt shr 8).toByte()
                result[offset + 1] = rateInt.toByte()
            }
        }
        return result
    }

    private fun handleSettingMaxValues(): ByteArray {
        val maxBolusInt = (state.maxBolus * 100).toInt()
        val maxBasalInt = (state.maxBasal * 100).toInt()
        val maxDailyInt = (state.maxDailyTotalUnits * 100).toInt()
        return byteArrayOf(
            (maxBolusInt shr 8).toByte(), maxBolusInt.toByte(),
            (maxBasalInt shr 8).toByte(), maxBasalInt.toByte(),
            (maxDailyInt shr 8).toByte(), maxDailyInt.toByte()
        )
    }

    private fun handleSettingShippingInfo(): ByteArray {
        val serial = state.serialNumber.toByteArray(Charsets.UTF_8).copyOf(10)
        val (year, month, day) = state.shippingDate
        val country = state.shippingCountry.map { (it.code - 65).toByte() }.toByteArray().copyOf(3)
        return serial + byteArrayOf(
            (year - 2000).toByte(), month.toByte(), day.toByte()
        ) + country
    }

    private fun handleSettingPumpTime(): ByteArray {
        val dt = DateTime(state.pumpTimeMillis)
        return byteArrayOf(
            dt.secondOfMinute.toByte(),
            dt.minuteOfHour.toByte(),
            dt.hourOfDay.toByte(),
            dt.dayOfMonth.toByte(),
            dt.monthOfYear.toByte(),
            (dt.year - 2000).toByte()
        )
    }

    private fun handleSettingUserOptions(): ByteArray {
        // MsgSettingUserOptions reads data[32] for lowReservoirRate — must be at least 33 bytes
        val data = ByteArray(33)
        data[0] = if (state.timeDisplayType24) 0x00 else 0x01
        data[1] = if (state.buttonScroll) 0x01 else 0x00
        data[2] = state.beepAndAlarm.toByte()
        data[3] = state.lcdOnTimeSec.toByte()
        data[4] = state.backlightOnTimeSec.toByte()
        data[5] = 0 // selected language
        data[8] = state.glucoseUnit.toByte()
        data[9] = state.shutdownHour.toByte()
        data[32] = state.lowReservoirRate.toByte()
        return data
    }

    private fun handleSettingActiveProfile(): ByteArray = byteArrayOf(state.activeProfile.toByte())

    // --- Bolus ---

    /** How long a bolus takes, see [EmulatorSpeed]. */
    @Volatile var speed: EmulatorSpeed = EmulatorSpeed.REAL

    val bolusRunning: Boolean get() = state.runningBolus != null

    /**
     * Starts a bolus that the pump gives step by step, 0.05 U at 12 s/U, and reports what is left with MsgBolusProgress
     * (0x0202): the whole amount at the start, then after each step. Seen on a real DanaRv2 (2026-11-10): a step every
     * 0.59 s. A start while a bolus runs is refused, UNVERIFIED how.
     */
    private fun handleBolusStart(params: ByteArray): ByteArray {
        if (params.size < 2 || state.runningBolus != null) return byteArrayOf(0x00)
        val amountInt = (params[0].toInt() and 0xFF shl 8) or (params[1].toInt() and 0xFF)
        val bolus = RunningBolus(amountInt)
        state.runningBolus = bolus
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: bolus ${amountInt / 100.0}U starts")
        Thread { deliver(bolus) }.start()
        return byteArrayOf(0x02) // success code = 2
    }

    /**
     * Gives [bolus] until it is all given or stopped, and sends MsgBolusStop (0x0101) at its end: the driver waits for
     * that message to end a bolus (`MsgBolusStop.handleMessage` sets `bolusStopped`). Seen on a real DanaRv2
     * (2026-11-10): a stop at 0.45 of 2 U gave 0.5, one more step than the last progress, and the pump had the bolus in
     * its history and status about 2 s after the end.
     */
    private fun deliver(bolus: RunningBolus) {
        val stepHundredths = if (state.variant == DanaRVariant.DANA_R_KOREAN) KOREAN_PROGRESS_STEP_HUNDREDTHS else PROGRESS_STEP_HUNDREDTHS
        val msPerStep = SECONDS_PER_UNIT * 1000L * stepHundredths / 100 / (if (speed == EmulatorSpeed.FAST) FAST_DIVISOR else 1)
        if (bolus.given == 0) {
            // After the answer to the start, as on the real pump.
            @Suppress("SleepInsteadOfDelay")
            Thread.sleep(FIRST_PROGRESS_MS)
            val all = bolus.hundredths
            onAdditionalResponse?.invoke(0x0202, byteArrayOf((all shr 8 and 0xFF).toByte(), (all and 0xFF).toByte()))
        }
        while (bolus.given < bolus.hundredths && !bolus.stopped) {
            @Suppress("SleepInsteadOfDelay")
            Thread.sleep(msPerStep.coerceAtLeast(1L))
            if (bolus.stopped) break
            val step = minOf(stepHundredths, bolus.hundredths - bolus.given)
            bolus.given += step
            synchronized(state) { state.reservoirRemainingUnits -= step / 100.0 }
            val remaining = bolus.hundredths - bolus.given
            // MsgBolusProgress: 2 bytes remaining insulin (int16 big-endian)
            onAdditionalResponse?.invoke(0x0202, byteArrayOf((remaining shr 8 and 0xFF).toByte(), (remaining and 0xFF).toByte()))
        }
        val given = synchronized(state) {
            // A DanaRv2 gives the step that runs when the stop comes, with no progress for it. A Korean DanaR stops at
            // its last progress.
            if (bolus.stopped && bolus.given < bolus.hundredths && state.variant != DanaRVariant.DANA_R_KOREAN) {
                val step = minOf(stepHundredths, bolus.hundredths - bolus.given)
                bolus.given += step
                state.reservoirRemainingUnits -= step / 100.0
            }
            val given = bolus.given / 100.0
            state.dailyTotalUnits += given
            state.givenBoluses += given
            state.runningBolus = null
            given
        }
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: bolus ends, ${given}U given")
        onAdditionalResponse?.invoke(0x0101, byteArrayOf(0x00))
        val end = state.pumpTimeMillis
        Thread {
            @Suppress("SleepInsteadOfDelay")
            Thread.sleep(LOGGED_AFTER_END_MS)
            synchronized(state) {
                state.historyStore.addEvent(DanaPump.HistoryEntry.BOLUS.value, end, bolus.given, 0)
                state.lastBolusAmount = given
                state.lastBolusTime = end
            }
        }.start()
    }

    /** The user stops the bolus: it ends at once with what it has given. */
    private fun handleBolusStop(): ByteArray {
        state.runningBolus?.stopped = true
        return byteArrayOf(0x00)
    }

    /** A bolus the user gives on the pump itself: given at once and logged as any bolus. False while a bolus runs. */
    fun bolusOnPump(units: Double): Boolean = synchronized(state) {
        if (state.runningBolus != null) return false
        val given = (units * 100).roundToInt()
        val time = state.pumpTimeMillis
        state.reservoirRemainingUnits -= given / 100.0
        state.historyStore.addEvent(DanaPump.HistoryEntry.BOLUS.value, time, given, 0)
        state.lastBolusAmount = given / 100.0
        state.lastBolusTime = time
        state.dailyTotalUnits += given / 100.0
        state.givenBoluses += given / 100.0
        true
    }

    /**
     * The user stops or starts again the insulin on the pump itself: the status says so, and the history logs SUSPEND_ON
     * or SUSPEND_OFF. UNVERIFIED: what a real DanaR does with a temp basal that runs at that time; the emulator keeps it.
     */
    fun suspendOnPump(suspended: Boolean): Boolean = synchronized(state) {
        state.isSuspended = suspended
        val code = if (suspended) DanaPump.HistoryEntry.SUSPEND_ON else DanaPump.HistoryEntry.SUSPEND_OFF
        state.historyStore.addEvent(code.value, state.pumpTimeMillis, 0, 0)
        true
    }

    // --- Temp basal ---

    private fun handleSetTempBasalStart(params: ByteArray): ByteArray {
        if (params.size >= 2) {
            val percent = params[0].toInt() and 0xFF
            val durationHours = params[1].toInt() and 0xFF
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = durationHours * 60
            state.tempBasalStartTime = state.pumpTimeMillis
            state.historyStore.addEvent(DanaPump.HistoryEntry.TEMP_START.value, state.tempBasalStartTime, percent, durationHours * 60)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: temp basal ${percent}% for ${state.tempBasalDurationMinutes}min")
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetTempBasalStop(): ByteArray {
        if (state.isTempBasalRunning) {
            state.historyStore.addEvent(DanaPump.HistoryEntry.TEMP_STOP.value, state.pumpTimeMillis, 0, 0)
        }
        state.isTempBasalRunning = false
        state.tempBasalPercent = 0
        return byteArrayOf(0x01)
    }

    // --- Extended bolus ---

    private fun handleSetExtendedBolusStart(params: ByteArray): ByteArray {
        if (params.size >= 3) {
            val amountInt = (params[0].toInt() and 0xFF shl 8) or (params[1].toInt() and 0xFF)
            val durationHalfHours = params[2].toInt() and 0xFF
            state.isExtendedBolusRunning = true
            state.extendedBolusAmount = amountInt / 100.0
            state.extendedBolusDurationHalfHours = durationHalfHours
            state.historyStore.addEvent(DanaPump.HistoryEntry.EXTENDED_START.value, state.pumpTimeMillis, amountInt, durationHalfHours * 30)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: extended bolus ${amountInt / 100.0}U for ${durationHalfHours * 30}min")
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetExtendedBolusStop(): ByteArray {
        if (state.isExtendedBolusRunning) {
            state.historyStore.addEvent(DanaPump.HistoryEntry.EXTENDED_STOP.value, state.pumpTimeMillis, 0, 0)
        }
        state.isExtendedBolusRunning = false
        state.extendedBolusAmount = 0.0
        return byteArrayOf(0x01)
    }

    // --- Set commands ---

    private fun handleSetSingleBasalProfile(params: ByteArray): ByteArray {
        storeBasalRates(params, offset = 0, profile = state.activeProfile)
        return byteArrayOf(0x01)
    }

    /**
     * MsgSetBasalProfile: `[profile number][24 rates, 2 bytes each, high byte first]`. The rates start after the profile
     * number. Read from byte 0 as in the single profile message, every rate was one byte off: 0.51 U/h was kept as
     * 130.56, and the next basal read reported it to AAPS.
     */
    private fun handleSetBasalProfile(params: ByteArray): ByteArray {
        if (params.isNotEmpty()) storeBasalRates(params, offset = 1, profile = (params[0].toInt() and 0xFF).coerceIn(0, 3))
        return byteArrayOf(0x01)
    }

    private fun storeBasalRates(params: ByteArray, offset: Int, profile: Int) {
        if (params.size < offset + 48) return
        for (i in 0 until 24) {
            val high = params[offset + i * 2].toInt() and 0xFF
            val low = params[offset + i * 2 + 1].toInt() and 0xFF
            state.basalProfiles[profile][i] = ((high shl 8) or low) / 100.0
        }
        updateCurrentBasal()
    }

    /**
     * The pump shows the rate of this hour in its active profile as its current basal, as a Dana RS does. It is set when
     * the profile changes, not each hour: a test may set it by hand.
     */
    private fun updateCurrentBasal() {
        val profile = state.basalProfiles.getOrNull(state.activeProfile) ?: return
        state.currentBasal = profile[DateTime(state.pumpTimeMillis).hourOfDay]
    }

    private fun handleSetUserOptions(params: ByteArray): ByteArray {
        if (params.size >= 9) {
            state.timeDisplayType24 = params[0].toInt() == 0
            state.buttonScroll = params[1].toInt() == 1
            state.beepAndAlarm = params[2].toInt() and 0xFF
            state.lcdOnTimeSec = params[3].toInt() and 0xFF
            state.backlightOnTimeSec = params[4].toInt() and 0xFF
            state.glucoseUnit = params[6].toInt() and 0xFF
            state.shutdownHour = params[7].toInt() and 0xFF
            state.lowReservoirRate = params[8].toInt() and 0xFF
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetActivateBasalProfile(params: ByteArray): ByteArray {
        if (params.isNotEmpty()) {
            state.activeProfile = params[0].toInt() and 0xFF
            updateCurrentBasal()
        }
        return byteArrayOf(0x01)
    }

    /** MsgSetTime: second, minute, hour, day, month, year - 2000, as [handleSettingPumpTime] reports the time. */
    private fun handleSetTime(params: ByteArray): ByteArray {
        if (params.size >= 6) {
            state.pumpTimeMillis = DateTime(
                2000 + (params[5].toInt() and 0xFF), params[4].toInt() and 0xFF, params[3].toInt() and 0xFF,
                params[2].toInt() and 0xFF, params[1].toInt() and 0xFF, params[0].toInt() and 0xFF
            ).millis
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetCarbsEntry(): ByteArray = byteArrayOf(0x01)

    // --- History ---

    private fun handlePCCommStart(): ByteArray = byteArrayOf(0x00)
    private fun handlePCCommStop(): ByteArray = byteArrayOf(0x00)

    /**
     * Serves the per-type review history behind the Pump-history screen (`DanaRExecutionService.loadHistory`
     * → `MsgHistoryBolus`/`MsgHistoryAlarm`/… on opcodes 0x3101-0x310A). The driver streams records for the
     * requested opcode and spins until a `MsgHistoryDone` (0x31F1) sets `historyDoneReceived`, so we push the
     * seeded records (via `buildReviewRecordData`, the shared review wire format the DanaRS emulator also uses)
     * on [command], then a 0x31F1 done. Records are seeded on [DanaRPumpState.reviewHistoryStore] with
     * `RecordTypes` codes and seconds=0 (the bolus branch reads byte 6 as its duration-low).
     *
     * Serves every per-type review opcode (0x3101-0x310A); the uniform 10-byte `buildReviewRecordData`
     * layout matches `MsgHistoryAll`'s branches for each type (bolus reads byte 6 as duration, so seed
     * bolus records minute-aligned; the rest read a 6-byte datetime-with-seconds).
     */
    private fun handleHistoryRequest(command: Int): ByteArray {
        val recordType = reviewRecordTypeForCommand(command) ?: return byteArrayOf(0xFF.toByte())
        if (command == 0x3101) state.bolusHistoryRequestCount++
        val records = state.reviewHistoryStore.getEventsAfter(0).filter { it.code == recordType }
        // Mirror handleApsHistoryEvents: the FIRST record is the direct reply, the rest stream via the
        // callback, then a MsgHistoryDone (0x31F1, 1-byte payload) the driver's loadHistory spins on.
        // Empty/empty-frame replies were rejected by the reader and hung the read - so both the reply and
        // the done carry a valid non-empty payload.
        Thread {
            @Suppress("SleepInsteadOfDelay")
            for (i in 1 until records.size) {
                Thread.sleep(10)
                onAdditionalResponse?.invoke(command, state.reviewHistoryStore.buildReviewRecordData(records[i]))
            }
            Thread.sleep(10)
            onAdditionalResponse?.invoke(0x31F1, byteArrayOf(0x00)) // MsgHistoryDone → historyDoneReceived = true
        }.start()
        return if (records.isEmpty()) byteArrayOf(0xFF.toByte())
        else state.reviewHistoryStore.buildReviewRecordData(records[0])
    }

    /** Maps a per-type review opcode (0x3101-0x310A) to its RecordTypes code, or null for unserved history. */
    private fun reviewRecordTypeForCommand(command: Int): Int? = when (command) {
        0x3101 -> RecordTypes.RECORD_TYPE_BOLUS.toInt()
        0x3102 -> RecordTypes.RECORD_TYPE_DAILY.toInt()
        0x3104 -> RecordTypes.RECORD_TYPE_GLUCOSE.toInt()
        0x3105 -> RecordTypes.RECORD_TYPE_ALARM.toInt()
        0x3106 -> RecordTypes.RECORD_TYPE_ERROR.toInt()
        0x3107 -> RecordTypes.RECORD_TYPE_CARBO.toInt()
        0x3108 -> RecordTypes.RECORD_TYPE_REFILL.toInt()
        0x3109 -> RecordTypes.RECORD_TYPE_SUSPEND.toInt()
        0x310A -> RecordTypes.RECORD_TYPE_BASALHOUR.toInt()
        else   -> null
    }

    private fun handleHistoryDone(): ByteArray = byteArrayOf(0x00)

    // --- Rv2 APS ---

    private fun handleApsStatus(): ByteArray = ByteArray(16) // simplified

    /**
     * MsgSetAPSTempBasalStartV2: the percent in 2 bytes (high first), then 160 for 30 minutes or 150 for 15. Read as the
     * hour based start before, 200 % was 0 % for 200 hours.
     */
    private fun handleApsSetTempBasal(params: ByteArray): ByteArray {
        if (params.size >= 3) {
            val percent = ((params[0].toInt() and 0xFF) shl 8) or (params[1].toInt() and 0xFF)
            val minutes = if ((params[2].toInt() and 0xFF) == APS_PARAM_30_MIN) 30 else 15
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = minutes
            state.tempBasalStartTime = state.pumpTimeMillis
            state.historyStore.addEvent(DanaPump.HistoryEntry.TEMP_START.value, state.tempBasalStartTime, percent, minutes)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: APS temp basal $percent% for ${minutes}min")
        }
        return byteArrayOf(0x01)
    }

    /**
     * Callback for sending additional history events after the first response.
     * Set by [EmulatorRfcommTransport] to enqueue extra packets on the response stream.
     */
    var onAdditionalResponse: ((Int, ByteArray) -> Unit)? = null

    init {
        // A pump read back after an app kill goes on with its bolus, as a real one does. Here, after the fields
        // the bolus thread reads.
        state.runningBolus?.let { bolus -> Thread { deliver(bolus) }.start() }
    }

    private fun handleApsHistoryEvents(params: ByteArray): ByteArray {
        // Local: MsgHistoryEventsV2 builds the request from a plain GregorianCalendar, and a DanaR
        // has no UTC mode — buildEventData answers in local time to match.
        val fromMillis = state.historyStore.parseFromTimestamp(params, usingUtc = false)
        val events = state.historyStore.getEventsAfter(fromMillis)

        if (events.isEmpty()) return state.historyStore.doneMarker

        // Return first event directly; send remaining + done via callback
        Thread {
            @Suppress("SleepInsteadOfDelay")
            for (i in 1 until events.size) {
                Thread.sleep(10)
                onAdditionalResponse?.invoke(0xE003, state.historyStore.buildEventData(events[i]))
            }
            Thread.sleep(10)
            onAdditionalResponse?.invoke(0xE003, state.historyStore.doneMarker)
        }.start()

        return state.historyStore.buildEventData(events[0])
    }

    private fun handleApsSetHistoryEntry(): ByteArray = byteArrayOf(0x01)

    companion object {

        /** The key of this emulator in [PumpEmulators]. */
        val KEY = PumpEmulators.Key<DanaRPumpEmulator>("danar")

        /** Seconds per unit of a bolus at the speed the driver sets by default, as the Dana RS. */
        private const val SECONDS_PER_UNIT = 12
        /** [EmulatorSpeed.FAST]: a bolus this many times shorter. */
        private const val FAST_DIVISOR = 20
        /** The bolus step a real DanaRv2 reported progress in, also when its bolus step setting is larger. */
        private const val PROGRESS_STEP_HUNDREDTHS = 5
        /**
         * A real Korean DanaR set to a bolus step of 0.1 U sent a progress every 0.1 U, 1.2 s apart (2026-11-10). After a
         * stop it had given what its last progress showed, twice.
         */
        private const val KOREAN_PROGRESS_STEP_HUNDREDTHS = 10
        /** How long after the end of a bolus a real DanaRv2 had it in its history: about 2 s. Not shortened in FAST. */
        private const val LOGGED_AFTER_END_MS = 2000L
        /** The first progress comes after the answer to the start. */
        private const val FIRST_PROGRESS_MS = 50L
        private const val APS_PARAM_30_MIN = 160
        private const val APS_PARAM_15_MIN = 150
    }
}
