package app.aaps.pump.danar.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import org.joda.time.DateTime

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
            0x3107, 0x3108, 0x3109, 0x310A -> handleHistoryRequest()

            0x31F1                         -> handleHistoryDone()
            0x41F1                         -> handleHistoryDone()
            0x41F2, 0x42F1, 0x42F2         -> handleHistoryRequest()

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

    private fun handleStatusTempBasal(): ByteArray {
        val running = state.isTempBasalRunning
        val elapsed = if (running) ((System.currentTimeMillis() - state.tempBasalStartTime) / 1000).toInt() else 0
        val durationCode = state.tempBasalDurationMinutes / 60

        return byteArrayOf(
            if (running) 0x01 else 0x00,                     // 0: status flags
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

    private fun handleBolusStart(params: ByteArray): ByteArray {
        if (params.size >= 2) {
            val amountInt = (params[0].toInt() and 0xFF shl 8) or (params[1].toInt() and 0xFF)
            val amount = amountInt / 100.0
            state.lastBolusAmount = amount
            state.lastBolusTime = System.currentTimeMillis()
            state.dailyTotalUnits += amount
            state.reservoirRemainingUnits -= amount
            state.historyStore.addEvent(DanaPump.HistoryEntry.BOLUS.value, state.lastBolusTime, amountInt, 0)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: bolus ${amount}U")

            // Simulate bolus delivery progress (MsgBolusProgress 0x0202)
            Thread {
                @Suppress("SleepInsteadOfDelay")
                val steps = 5
                val stepAmount = amountInt / steps
                for (i in 1..steps) {
                    Thread.sleep(200)
                    val remaining = amountInt - minOf(stepAmount * i, amountInt)
                    // MsgBolusProgress response: 2 bytes remaining insulin (int16 big-endian)
                    val progressData = byteArrayOf(
                        (remaining shr 8 and 0xFF).toByte(),
                        (remaining and 0xFF).toByte()
                    )
                    onAdditionalResponse?.invoke(0x0202, progressData)
                }
            }.start()
        }
        return byteArrayOf(0x02) // success code = 2
    }

    private fun handleBolusStop(): ByteArray = byteArrayOf(0x00)

    // --- Temp basal ---

    private fun handleSetTempBasalStart(params: ByteArray): ByteArray {
        if (params.size >= 2) {
            val percent = params[0].toInt() and 0xFF
            val durationHours = params[1].toInt() and 0xFF
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = durationHours * 60
            state.tempBasalStartTime = System.currentTimeMillis()
            state.historyStore.addEvent(DanaPump.HistoryEntry.TEMP_START.value, state.tempBasalStartTime, percent, durationHours * 60)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: temp basal ${percent}% for ${state.tempBasalDurationMinutes}min")
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetTempBasalStop(): ByteArray {
        if (state.isTempBasalRunning) {
            state.historyStore.addEvent(DanaPump.HistoryEntry.TEMP_STOP.value, System.currentTimeMillis(), 0, 0)
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
            state.historyStore.addEvent(DanaPump.HistoryEntry.EXTENDED_START.value, System.currentTimeMillis(), amountInt, durationHalfHours * 30)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: extended bolus ${amountInt / 100.0}U for ${durationHalfHours * 30}min")
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetExtendedBolusStop(): ByteArray {
        if (state.isExtendedBolusRunning) {
            state.historyStore.addEvent(DanaPump.HistoryEntry.EXTENDED_STOP.value, System.currentTimeMillis(), 0, 0)
        }
        state.isExtendedBolusRunning = false
        state.extendedBolusAmount = 0.0
        return byteArrayOf(0x01)
    }

    // --- Set commands ---

    private fun handleSetSingleBasalProfile(params: ByteArray): ByteArray {
        if (params.size >= 48) {
            for (i in 0 until 24) {
                val rate = ((params[i * 2].toInt() and 0xFF shl 8) or (params[i * 2 + 1].toInt() and 0xFF)) / 100.0
                state.basalProfiles[state.activeProfile][i] = rate
            }
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetBasalProfile(params: ByteArray): ByteArray = handleSetSingleBasalProfile(params)

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
        }
        return byteArrayOf(0x01)
    }

    private fun handleSetTime(params: ByteArray): ByteArray {
        // Time is set; just acknowledge
        return byteArrayOf(0x01)
    }

    private fun handleSetCarbsEntry(): ByteArray = byteArrayOf(0x01)

    // --- History ---

    private fun handlePCCommStart(): ByteArray = byteArrayOf(0x00)
    private fun handlePCCommStop(): ByteArray = byteArrayOf(0x00)

    private fun handleHistoryRequest(): ByteArray {
        // Return empty history with "done" flag — byte[0]=0xFF signals done
        return byteArrayOf(0xFF.toByte())
    }

    private fun handleHistoryDone(): ByteArray = byteArrayOf(0x00)

    // --- Rv2 APS ---

    private fun handleApsStatus(): ByteArray = ByteArray(16) // simplified

    private fun handleApsSetTempBasal(params: ByteArray): ByteArray = handleSetTempBasalStart(params)

    /**
     * Callback for sending additional history events after the first response.
     * Set by [EmulatorRfcommTransport] to enqueue extra packets on the response stream.
     */
    var onAdditionalResponse: ((Int, ByteArray) -> Unit)? = null

    private fun handleApsHistoryEvents(params: ByteArray): ByteArray {
        val fromMillis = state.historyStore.parseFromTimestamp(params)
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
}
