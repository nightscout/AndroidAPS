package app.aaps.pump.danars.emulator

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.emulator.HistoryEvent
import app.aaps.pump.danars.encryption.BleEncryption
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
     * @param type packet type (TYPE_NOTIFY for notifications, TYPE_RESPONSE for follow-up responses)
     * @param opCode the opcode
     * @param data the data bytes
     */
    var onSpontaneousMessage: ((type: Int, opCode: Int, data: ByteArray) -> Unit)? = null

    /** Delay between history events in [processApsHistoryEvents]. Tests can set to 0. */
    var historyEventDelayMs: Long = 10L

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
        if (params.size >= 2) {
            val percent = params[0].toInt() and 0xFF
            val durationHours = params[1].toInt() and 0xFF
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = durationHours * 60
            state.tempBasalStartTime = System.currentTimeMillis()
            addHistoryEvent(DanaPump.HistoryEntry.TEMP_START.value, state.tempBasalStartTime, percent, durationHours * 60)
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processCancelTemporaryBasal(): ByteArray {
        if (state.isTempBasalRunning) {
            addHistoryEvent(DanaPump.HistoryEntry.TEMP_STOP.value, System.currentTimeMillis(), 0, 0)
        }
        state.isTempBasalRunning = false
        state.tempBasalPercent = 0
        state.tempBasalDurationMinutes = 0
        return byteArrayOf(0x00) // OK
    }

    private fun processApsSetTemporaryBasal(params: ByteArray): ByteArray {
        if (params.size >= 3) {
            val percent = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
            val durationMinutes = if (params[2].toInt() and 0xFF == 150) 15 else 30
            state.isTempBasalRunning = true
            state.tempBasalPercent = percent
            state.tempBasalDurationMinutes = durationMinutes
            state.tempBasalStartTime = System.currentTimeMillis()
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
            }
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetProfileNumber(params: ByteArray): ByteArray {
        if (params.isNotEmpty()) {
            state.activeProfileNumber = params[0].toInt() and 0xFF
        }
        return byteArrayOf(0x00) // OK
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

    private fun processSetStepBolusStart(params: ByteArray): ByteArray {
        if (params.size >= 3) {
            val amountHundredths = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
            val amount = amountHundredths / 100.0
            val speed = params[2].toInt() and 0xFF // seconds per unit (12, 30, or 60)
            state.lastBolusAmount = amount
            state.lastBolusTime = System.currentTimeMillis()
            state.dailyTotalUnits += amount
            state.reservoirRemainingUnits -= amount

            // Record history event
            addHistoryEvent(DanaPump.HistoryEntry.BOLUS.value, state.lastBolusTime, amountHundredths, 0)

            // Schedule bolus delivery notifications on a separate thread.
            // Delivery takes amount * speed seconds (e.g., 1U at speed 12 = 12 seconds).
            // Send progress every second to match the app's expected delivery timing.
            Thread {
                @Suppress("SleepInsteadOfDelay")
                val intervalMs = state.bolusDeliveryIntervalMs
                val deliveryTimeMs = if (intervalMs > 0) (amount * speed * 1000).toLong() else 0L
                val steps = if (intervalMs > 0) maxOf(1, (deliveryTimeMs / intervalMs).toInt()) else 1
                val stepAmount = amountHundredths / steps
                for (i in 1..steps) {
                    if (intervalMs > 0) Thread.sleep(intervalMs)
                    val delivered = minOf(stepAmount * i, amountHundredths)
                    val rateData = byteArrayOf(
                        (delivered and 0xFF).toByte(),
                        ((delivered shr 8) and 0xFF).toByte()
                    )
                    onSpontaneousMessage?.invoke(BleEncryption.DANAR_PACKET__TYPE_NOTIFY, BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY, rateData)
                }
                // NOTIFY__DELIVERY_COMPLETE
                val completeData = byteArrayOf(
                    (amountHundredths and 0xFF).toByte(),
                    ((amountHundredths shr 8) and 0xFF).toByte()
                )
                onSpontaneousMessage?.invoke(BleEncryption.DANAR_PACKET__TYPE_NOTIFY, BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE, completeData)
            }.start()
        }
        return byteArrayOf(0x00) // OK (error code 0 = no error)
    }

    private fun processSetStepBolusStop(): ByteArray = byteArrayOf(0x00) // OK

    private fun processSetExtendedBolus(params: ByteArray): ByteArray {
        if (params.size >= 3) {
            val amountHundredths = (params[0].toInt() and 0xFF) or ((params[1].toInt() and 0xFF) shl 8)
            val durationHalfHours = params[2].toInt() and 0xFF
            state.isExtendedBolusRunning = true
            state.extendedBolusAmount = amountHundredths / 100.0
            state.extendedBolusDurationHalfHours = durationHalfHours
            addHistoryEvent(DanaPump.HistoryEntry.EXTENDED_START.value, System.currentTimeMillis(), amountHundredths, durationHalfHours * 30)
        }
        return byteArrayOf(0x00) // OK
    }

    private fun processSetExtendedBolusCancel(): ByteArray {
        if (state.isExtendedBolusRunning) {
            addHistoryEvent(DanaPump.HistoryEntry.EXTENDED_STOP.value, System.currentTimeMillis(), 0, 0)
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
            ldt.monthNumber.toByte(),
            ldt.dayOfMonth.toByte(),
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
            ldt.monthNumber.toByte(),
            ldt.dayOfMonth.toByte(),
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
        val fromMillis = store.parseFromTimestamp(params)
        val events = store.getEventsAfter(fromMillis)

        if (events.isEmpty()) return store.doneMarker

        // Return first event directly; send remaining + done via spontaneous messages
        Thread {
            @Suppress("SleepInsteadOfDelay")
            for (i in 1 until events.size) {
                if (historyEventDelayMs > 0) Thread.sleep(historyEventDelayMs)
                val data = buildHistoryEventData(events[i], (i + 1).toShort())
                onSpontaneousMessage?.invoke(
                    BleEncryption.DANAR_PACKET__TYPE_RESPONSE,
                    BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS,
                    data
                )
            }
            Thread.sleep(10)
            onSpontaneousMessage?.invoke(
                BleEncryption.DANAR_PACKET__TYPE_RESPONSE,
                BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS,
                store.doneMarker
            )
        }.start()

        return buildHistoryEventData(events[0], 1)
    }

    /**
     * Build a history event data payload in the format matching the pump's time mode.
     */
    private fun buildHistoryEventData(event: HistoryEvent, id: Short): ByteArray {
        return if (state.usingUTC) {
            state.historyStore.buildEventDataUtc(event, id)
        } else {
            state.historyStore.buildEventData(event)
        }
    }

    private fun addHistoryEvent(code: Int, timestamp: Long, param1: Int, param2: Int) {
        state.historyStore.addEvent(code, timestamp, param1, param2)
    }

    private fun processApsSetEventHistory(params: ByteArray): ByteArray = byteArrayOf(0x00) // OK

    // --- Helpers ---

    private fun putIntToArray(array: ByteArray, position: Int, value: Int) {
        array[position] = (value and 0xFF).toByte()
        if (position + 1 < array.size) {
            array[position + 1] = ((value shr 8) and 0xFF).toByte()
        }
    }
}
