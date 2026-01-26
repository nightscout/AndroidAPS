package app.aaps.pump.medtrum.comm.packets

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.comm.enums.BasalEndReason
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.BolusType
import app.aaps.pump.medtrum.comm.enums.CommandType.GET_RECORD
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.extension.toFloat
import app.aaps.pump.medtrum.extension.toInt
import app.aaps.pump.medtrum.extension.toLong
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class GetRecordPacket(injector: HasAndroidInjector, private val recordIndex: Int) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var medtrumTimeUtil: MedtrumTimeUtil

    companion object {

        private const val RESP_RECORD_HEADER_START = 6
        private const val RESP_RECORD_HEADER_END = RESP_RECORD_HEADER_START + 1
        private const val RESP_RECORD_UNKNOWN_START = RESP_RECORD_HEADER_END
        private const val RESP_RECORD_UNKNOWN_END = RESP_RECORD_UNKNOWN_START + 1
        private const val RESP_RECORD_TYPE_START = RESP_RECORD_UNKNOWN_END
        private const val RESP_RECORD_TYPE_END = RESP_RECORD_TYPE_START + 1
        private const val RESP_RECORD_UNKNOWN1_START = RESP_RECORD_TYPE_END
        private const val RESP_RECORD_UNKNOWN1_END = RESP_RECORD_UNKNOWN1_START + 1
        private const val RESP_RECORD_SERIAL_START = RESP_RECORD_UNKNOWN1_END
        private const val RESP_RECORD_SERIAL_END = RESP_RECORD_SERIAL_START + 4
        private const val RESP_RECORD_PATCH_ID_START = RESP_RECORD_SERIAL_END
        private const val RESP_RECORD_PATCH_ID_END = RESP_RECORD_PATCH_ID_START + 2
        private const val RESP_RECORD_SEQUENCE_START = RESP_RECORD_PATCH_ID_END
        private const val RESP_RECORD_SEQUENCE_END = RESP_RECORD_SEQUENCE_START + 2
        private const val RESP_RECORD_DATA_START = RESP_RECORD_SEQUENCE_END

        private const val VALID_HEADER = 170
        private const val BOLUS_RECORD = 1
        private const val BOLUS_RECORD_ALT = 65
        private const val BASAL_RECORD = 2
        private const val BASAL_RECORD_ALT = 66
        private const val ALARM_RECORD = 3
        private const val AUTO_RECORD = 4
        private const val TIME_SYNC_RECORD = 5
        private const val AUTO1_RECORD = 6
        private const val AUTO2_RECORD = 7
        private const val AUTO3_RECORD = 8
        private const val TDD_RECORD = 9

    }

    init {
        opCode = GET_RECORD.code
        expectedMinRespLength = RESP_RECORD_DATA_START
    }

    override fun getRequest(): ByteArray {
        return byteArrayOf(opCode) + recordIndex.toByteArray(2) + medtrumPump.patchId.toByteArray(2)
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val recordHeader = data.copyOfRange(RESP_RECORD_HEADER_START, RESP_RECORD_HEADER_END).toInt()
            val recordUnknown = data.copyOfRange(RESP_RECORD_UNKNOWN_START, RESP_RECORD_UNKNOWN_END).toInt()
            val recordType = data.copyOfRange(RESP_RECORD_TYPE_START, RESP_RECORD_TYPE_END).toInt()
            val recordSerial = data.copyOfRange(RESP_RECORD_SERIAL_START, RESP_RECORD_SERIAL_END).toLong()
            val recordPatchId = data.copyOfRange(RESP_RECORD_PATCH_ID_START, RESP_RECORD_PATCH_ID_END).toLong()
            val recordSequence = data.copyOfRange(RESP_RECORD_SEQUENCE_START, RESP_RECORD_SEQUENCE_END).toInt()

            aapsLogger.run {
                debug(
                    LTag.PUMPCOMM,
                    "GetRecordPacket HandleResponse: Record header: $recordHeader, unknown: $recordUnknown, type: $recordType, serial: $recordSerial, patchId: $recordPatchId, sequence: $recordSequence"
                )
            }

            if (recordHeader == VALID_HEADER) {
                when (recordType) {
                    BOLUS_RECORD, BOLUS_RECORD_ALT -> {
                        handleBolusRecord(data)
                    }

                    BASAL_RECORD, BASAL_RECORD_ALT -> {
                        handleBasalRecord(data)
                    }

                    ALARM_RECORD                   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: ALARM_RECORD")
                    }

                    AUTO_RECORD                    -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: AUTO_RECORD")
                    }

                    TIME_SYNC_RECORD               -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: TIME_SYNC_RECORD")
                    }

                    AUTO1_RECORD                   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: AUTO1_RECORD")
                    }

                    AUTO2_RECORD                   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: AUTO2_RECORD")
                    }

                    AUTO3_RECORD                   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: AUTO3_RECORD")
                    }

                    TDD_RECORD                     -> {
                        handleTddRecord(data)
                    }

                    else                           -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: Unknown record type: $recordType")
                    }
                }
            } else {
                aapsLogger.error(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: Invalid record header")
            }

            // Update sequence number
            medtrumPump.syncedSequenceNumber = recordSequence // Assume sync upwards
        }

        return success
    }

    private fun handleBolusRecord(data: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BOLUS_RECORD")
        val typeAndWizard = data.copyOfRange(RESP_RECORD_DATA_START, RESP_RECORD_DATA_START + 1).toInt()
        val bolusCause = data.copyOfRange(RESP_RECORD_DATA_START + 1, RESP_RECORD_DATA_START + 2).toInt()
        val unknown = data.copyOfRange(RESP_RECORD_DATA_START + 2, RESP_RECORD_DATA_START + 4).toInt()
        val bolusStartTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_RECORD_DATA_START + 4, RESP_RECORD_DATA_START + 8).toLong())
        val bolusNormalAmount = data.copyOfRange(RESP_RECORD_DATA_START + 8, RESP_RECORD_DATA_START + 10).toInt() * 0.05
        val bolusNormalDelivered = data.copyOfRange(RESP_RECORD_DATA_START + 10, RESP_RECORD_DATA_START + 12).toInt() * 0.05
        val bolusExtendedAmount = data.copyOfRange(RESP_RECORD_DATA_START + 12, RESP_RECORD_DATA_START + 14).toInt() * 0.05
        val bolusExtendedDuration = T.mins(data.copyOfRange(RESP_RECORD_DATA_START + 14, RESP_RECORD_DATA_START + 16).toLong()).msecs()
        val bolusExtendedDelivered = data.copyOfRange(RESP_RECORD_DATA_START + 16, RESP_RECORD_DATA_START + 18).toInt() * 0.05
        val bolusCarb = data.copyOfRange(RESP_RECORD_DATA_START + 18, RESP_RECORD_DATA_START + 20).toInt()
        val bolusGlucose = data.copyOfRange(RESP_RECORD_DATA_START + 20, RESP_RECORD_DATA_START + 22).toInt()
        val bolusIOB = data.copyOfRange(RESP_RECORD_DATA_START + 22, RESP_RECORD_DATA_START + 24).toInt()
        val unknown1 = data.copyOfRange(RESP_RECORD_DATA_START + 24, RESP_RECORD_DATA_START + 26).toInt()
        val unknown2 = data.copyOfRange(RESP_RECORD_DATA_START + 26, RESP_RECORD_DATA_START + 28).toInt()
        val bolusType = enumValues<BolusType>()[typeAndWizard and 0x0F]
        val bolusWizard = (typeAndWizard and 0xF0) != 0
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "GetRecordPacket HandleResponse: BOLUS_RECORD: typeAndWizard: $typeAndWizard, bolusCause: $bolusCause, unknown: $unknown, bolusStartTime: $bolusStartTime, " +
                "bolusNormalAmount: $bolusNormalAmount, bolusNormalDelivered: $bolusNormalDelivered, bolusExtendedAmount: $bolusExtendedAmount, bolusExtendedDuration: " +
                "$bolusExtendedDuration, " + "bolusExtendedDelivered: $bolusExtendedDelivered, bolusCarb: $bolusCarb, bolusGlucose: $bolusGlucose, bolusIOB: $bolusIOB, unknown1: $unknown1, unknown2: $unknown2, " + "bolusType: $bolusType, bolusWizard: $bolusWizard"
        )

        when (bolusType) {
            BolusType.NORMAL   -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(bolusStartTime, bolusNormalDelivered)
                var newRecord = false
                if (detailedBolusInfo != null) {
                    val syncOk = pumpSync.syncBolusWithTempId(
                        timestamp = bolusStartTime,
                        amount = PumpInsulin(bolusNormalDelivered),
                        temporaryId = detailedBolusInfo.timestamp,
                        type = detailedBolusInfo.bolusType,
                        pumpId = bolusStartTime,
                        pumpType = medtrumPump.pumpType(),
                        pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                    )
                    if (!syncOk) {
                        aapsLogger.warn(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BOLUS_RECORD: Failed to sync bolus with tempId: ${detailedBolusInfo.timestamp}")
                        // detailedInfo can be from another similar record. Reinsert
                        detailedBolusInfoStorage.add(detailedBolusInfo)
                    }
                } else {
                    newRecord = pumpSync.syncBolusWithPumpId(
                        timestamp = bolusStartTime,
                        amount = PumpInsulin(bolusNormalDelivered),
                        type = null,
                        pumpId = bolusStartTime,
                        pumpType = medtrumPump.pumpType(),
                        pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                    )
                }

                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "from record: ${newRecordInfo(newRecord)}EVENT BOLUS ${dateUtil.dateAndTimeString(bolusStartTime)} ($bolusStartTime) Bolus: ${bolusNormalDelivered}U "
                )
                if (bolusStartTime > medtrumPump.lastBolusTime) {
                    medtrumPump.lastBolusTime = bolusStartTime
                    medtrumPump.lastBolusAmount = bolusNormalDelivered
                }
            }

            BolusType.EXTENDED -> {
                val newRecord = pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = bolusStartTime,
                    rate = PumpRate(bolusExtendedDelivered),
                    duration = bolusExtendedDuration,
                    isEmulatingTB = false,
                    pumpId = bolusStartTime,
                    pumpType = medtrumPump.pumpType(),
                    pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                )
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "from record: ${newRecordInfo(newRecord)}EVENT EXTENDED BOLUS ${dateUtil.dateAndTimeString(bolusStartTime)} ($bolusStartTime) Bolus: ${bolusNormalDelivered}U "
                )
            }

            BolusType.COMBI    -> {
                // Note, this should never happen, as we don't use combo bolus
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(bolusStartTime, bolusNormalDelivered)
                val newRecord = pumpSync.syncBolusWithPumpId(
                    timestamp = bolusStartTime,
                    amount = PumpInsulin(bolusNormalDelivered),
                    type = detailedBolusInfo?.bolusType,
                    pumpId = bolusStartTime,
                    pumpType = medtrumPump.pumpType(),
                    pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                )
                pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = bolusStartTime,
                    rate = PumpRate(bolusExtendedDelivered),
                    duration = bolusExtendedDuration,
                    isEmulatingTB = false,
                    pumpId = bolusStartTime,
                    pumpType = medtrumPump.pumpType(),
                    pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                )
                aapsLogger.error(
                    LTag.PUMPCOMM,
                    "from record: ${newRecordInfo(newRecord)}EVENT COMBO BOLUS ${dateUtil.dateAndTimeString(bolusStartTime)} ($bolusStartTime) Bolus: ${bolusNormalDelivered}U Extended: $bolusExtendedDelivered THIS SHOULD NOT HAPPEN!!!"
                )
                if (!newRecord && detailedBolusInfo != null) {
                    // detailedInfo can be from another similar record. Reinsert
                    detailedBolusInfoStorage.add(detailedBolusInfo)
                }
                if (bolusStartTime > medtrumPump.lastBolusTime) {
                    medtrumPump.lastBolusTime = bolusStartTime
                    medtrumPump.lastBolusAmount = bolusNormalDelivered
                }
            }

            else               -> {
                aapsLogger.error(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BOLUS_RECORD: Unknown bolus type: $bolusType")
            }
        }
    }

    private fun handleBasalRecord(data: ByteArray) {
        val recordPatchId = data.copyOfRange(RESP_RECORD_PATCH_ID_START, RESP_RECORD_PATCH_ID_END).toLong()
        val recordSequence = data.copyOfRange(RESP_RECORD_SEQUENCE_START, RESP_RECORD_SEQUENCE_END).toInt()

        val basalStartTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_RECORD_DATA_START, RESP_RECORD_DATA_START + 4).toLong())
        val basalEndTime = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_RECORD_DATA_START + 4, RESP_RECORD_DATA_START + 8).toLong())
        val basalType = enumValues<BasalType>()[data.copyOfRange(RESP_RECORD_DATA_START + 8, RESP_RECORD_DATA_START + 9).toInt()]
        val basalEndReasonInt = data.copyOfRange(RESP_RECORD_DATA_START + 9, RESP_RECORD_DATA_START + 10).toInt()
        val basalEndReason = enumValues<BasalEndReason>().getOrNull(basalEndReasonInt)
        val basalRate = data.copyOfRange(RESP_RECORD_DATA_START + 10, RESP_RECORD_DATA_START + 12).toInt() * 0.05
        val basalDelivered = data.copyOfRange(RESP_RECORD_DATA_START + 12, RESP_RECORD_DATA_START + 14).toInt() * 0.05
        val basalPercent = data.copyOfRange(RESP_RECORD_DATA_START + 14, RESP_RECORD_DATA_START + 16).toInt()

        aapsLogger.debug(
            LTag.PUMPCOMM,
            "GetRecordPacket HandleResponse: BASAL_RECORD: Start: $basalStartTime, End: $basalEndTime, Type: $basalType, EndReason: $basalEndReason, Rate: $basalRate, Delivered: $basalDelivered, Percent: $basalPercent"
        )

        when (basalType) {
            BasalType.STANDARD                               -> {
                aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BASAL_RECORD: Standard basal")
                // If we are here it means the basal has ended
            }

            BasalType.ABSOLUTE_TEMP, BasalType.RELATIVE_TEMP -> {
                aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BASAL_RECORD: temp basal")
                var duration = (basalEndTime - basalStartTime)
                // Work around for pumpSync not accepting 0 duration.
                // sometimes we get 0 duration for very short basal because the pump only reports time in seconds
                if (duration < 250) duration = 250 // 250ms to make sure AAPS accepts it

                val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = basalStartTime,
                    rate = PumpRate(if (basalType == BasalType.ABSOLUTE_TEMP) basalRate else basalPercent.toDouble()),
                    duration = duration,
                    isAbsolute = (basalType == BasalType.ABSOLUTE_TEMP),
                    type = PumpSync.TemporaryBasalType.NORMAL,
                    pumpId = basalStartTime,
                    pumpType = medtrumPump.pumpType(),
                    pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                )
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "handleBasalStatusUpdate from record: ${newRecordInfo(newRecord)}EVENT TEMP_SYNC: ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) " +
                        "Rate: $basalRate Duration: $duration"
                )
            }

            in BasalType.SUSPEND_LOW_GLUCOSE..BasalType.STOP -> {
                aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BASAL_RECORD: Suspend basal")
                // Never seen a packet like this from a pump, even when suspended by app, but leave it in just in case
                val duration = (basalEndTime - basalStartTime)
                val newRecord = pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = basalStartTime,
                    rate = PumpRate(0.0),
                    duration = duration,
                    isAbsolute = true,
                    type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                    pumpId = basalStartTime,
                    pumpType = medtrumPump.pumpType(),
                    pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
                )
                aapsLogger.debug(
                    LTag.PUMPCOMM,
                    "handleBasalStatusUpdate from record: ${newRecordInfo(newRecord)}EVENT SUSPEND: ($basalType) ${dateUtil.dateAndTimeString(basalStartTime)} ($basalStartTime) " +
                        "Rate: $basalRate Duration: $duration"
                )
            }

            else                                             -> {
                aapsLogger.error(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BASAL_RECORD: Unknown basal type: $basalType")
            }
        }

        if (basalEndReason == null) {
            aapsLogger.error(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: BASAL_RECORD: Unknown basal end reason: $basalEndReasonInt")
        } else if (basalEndReason.isSuspendedByPump()) {
            // Pump doesn't seem to sync suspend/stop explicitly, so we need to do it here
            // Sync suspend using handleBasalStatusUpdate to make sure other variables are updated as well
            // Check if we don't have another temp basal running which is not a suspend
            // (record maybe to old and information not valid). For suspends we want to update timestamp
            val expectedTemporaryBasal = pumpSync.expectedPumpState().temporaryBasal
            if (expectedTemporaryBasal == null || expectedTemporaryBasal.timestamp <= basalEndTime || expectedTemporaryBasal.duration == T.mins(MedtrumPump.FAKE_TBR_LENGTH).msecs()) {
                aapsLogger.warn(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: Got suspended end reason, syncing suspend")
                medtrumPump.handleBasalStatusUpdate(BasalType.fromBasalEndReason(basalEndReason), 0.0, recordSequence, recordPatchId, basalEndTime)
            }
        }
    }

    private fun handleTddRecord(data: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "GetRecordPacket HandleResponse: TDD_RECORD")
        val timestamp = medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(data.copyOfRange(RESP_RECORD_DATA_START, RESP_RECORD_DATA_START + 4).toLong())
        val timeZoneOffset = data.copyOfRange(RESP_RECORD_DATA_START + 4, RESP_RECORD_DATA_START + 6).toInt()
        val tddMinutes = data.copyOfRange(RESP_RECORD_DATA_START + 6, RESP_RECORD_DATA_START + 8).toInt()
        val glucoseRecordTime = data.copyOfRange(RESP_RECORD_DATA_START + 8, RESP_RECORD_DATA_START + 12).toLong()
        val tdd = data.copyOfRange(RESP_RECORD_DATA_START + 12, RESP_RECORD_DATA_START + 16).toFloat()
        val basalTdd = data.copyOfRange(RESP_RECORD_DATA_START + 16, RESP_RECORD_DATA_START + 20).toFloat()
        val glucose = data.copyOfRange(RESP_RECORD_DATA_START + 20, RESP_RECORD_DATA_START + 24).toFloat()
        val unknown = data.copyOfRange(RESP_RECORD_DATA_START + 24, RESP_RECORD_DATA_START + 28).toFloat()
        val meanSomething = data.copyOfRange(RESP_RECORD_DATA_START + 28, RESP_RECORD_DATA_START + 32).toFloat()
        val usedTdd = data.copyOfRange(RESP_RECORD_DATA_START + 32, RESP_RECORD_DATA_START + 36).toFloat()
        val usedIBasal = data.copyOfRange(RESP_RECORD_DATA_START + 36, RESP_RECORD_DATA_START + 40).toFloat()
        val usedSgBasal = data.copyOfRange(RESP_RECORD_DATA_START + 40, RESP_RECORD_DATA_START + 44).toFloat()
        val usedUMax = data.copyOfRange(RESP_RECORD_DATA_START + 44, RESP_RECORD_DATA_START + 48).toFloat()
        val newTdd = data.copyOfRange(RESP_RECORD_DATA_START + 48, RESP_RECORD_DATA_START + 52).toFloat()
        val newIBasal = data.copyOfRange(RESP_RECORD_DATA_START + 52, RESP_RECORD_DATA_START + 56).toFloat()
        val newSgBasal = data.copyOfRange(RESP_RECORD_DATA_START + 56, RESP_RECORD_DATA_START + 60).toFloat()
        val newUMax = data.copyOfRange(RESP_RECORD_DATA_START + 60, RESP_RECORD_DATA_START + 64).toFloat()

        aapsLogger.debug(
            LTag.PUMPCOMM, "TDD_RECORD: timestamp: $timestamp, timeZoneOffset: $timeZoneOffset, tddMinutes: $tddMinutes, glucoseRecordTime: $glucoseRecordTime, tdd: " +
                "$tdd, basalTdd: $basalTdd, glucose: $glucose, unknown: $unknown, meanSomething: $meanSomething, usedTdd: $usedTdd, usedIBasal: $usedIBasal, usedSgBasal: " +
                "$usedSgBasal, usedUMax: $usedUMax, newTdd: $newTdd, newIBasal: $newIBasal, newSgBasal: $newSgBasal, newUMax: $newUMax"
        )

        val newRecord = pumpSync.createOrUpdateTotalDailyDose(
            timestamp = timestamp,
            bolusAmount = (tdd - basalTdd).toDouble(),
            basalAmount = basalTdd.toDouble(),
            totalAmount = tdd.toDouble(),
            pumpId = timestamp,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
        )

        aapsLogger.debug(
            LTag.PUMPCOMM,
            "handleBasalStatusUpdate from record: ${newRecordInfo(newRecord)}EVENT TDD: ${dateUtil.dateAndTimeString(timestamp)} ($timestamp) " +
                "TDD: $tdd, BasalTDD: $basalTdd, BolusTDD: ${tdd - basalTdd}"
        )
    }

    private fun newRecordInfo(newRecord: Boolean): String {
        return if (newRecord) "**NEW** " else ""
    }
}
