package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump

import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RecordDecodeStatus
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType.Companion.getByCode
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusWizardDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.DailyTotalsDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpBolusType
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.core.utils.DateTimeUtil
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.and

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
@Singleton
class MedtronicPumpHistoryDecoder @Inject constructor(
    aapsLogger: AAPSLogger,
    medtronicUtil: MedtronicUtil,
    bitUtils: ByteUtil
) : MedtronicHistoryDecoder<PumpHistoryEntry>(aapsLogger, medtronicUtil, bitUtils) {

    //private var tbrPreviousRecord: PumpHistoryEntry? = null
    private var changeTimeRecord: PumpHistoryEntry? = null

    override fun createRecords(dataClearInput: MutableList<Byte>): MutableList<PumpHistoryEntry> {
        prepareStatistics()
        var counter = 0
        var record = 0
        var incompletePacket: Boolean
        val outList: MutableList<PumpHistoryEntry> = mutableListOf()
        var skipped: String? = null
        if (dataClearInput.size == 0) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Empty page.")
            return outList
        }
        do {
            val opCode: Int = dataClearInput[counter].toInt()
            var special = false
            incompletePacket = false
            var skippedRecords = false
            if (opCode == 0) {
                counter++
                if (skipped == null) skipped = "0x00" else skipped += " 0x00"
                continue
            } else {
                if (skipped != null) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, " ... Skipped $skipped")
                    skipped = null
                    skippedRecords = true
                }
            }
            if (skippedRecords) {
                aapsLogger.error(LTag.PUMPBTCOMM, "We had some skipped bytes, which might indicate error in pump history. Please report this problem.")
            }
            val entryType = getByCode(opCode.toByte())
            val pe = PumpHistoryEntry()
            pe.setEntryType(medtronicUtil.medtronicPumpModel, entryType, if (entryType == PumpHistoryEntryType.UnknownBasePacket) opCode.toByte() else null)
            pe.offset = counter
            counter++
            if (counter >= 1022) {
                break
            }
            val listRawData: MutableList<Byte> = ArrayList()
            listRawData.add(opCode.toByte())
            if (entryType === PumpHistoryEntryType.UnabsorbedInsulin
                || entryType === PumpHistoryEntryType.UnabsorbedInsulin512) {
                val elements: Int = dataClearInput[counter].toInt()
                listRawData.add(elements.toByte())
                counter++
                val els = getUnsignedInt(elements)
                for (k in 0 until els - 2) {
                    if (counter < 1022) {
                        listRawData.add(dataClearInput[counter])
                        counter++
                    }
                }
                special = true
            } else {
                for (j in 0 until entryType.getTotalLength(medtronicUtil.medtronicPumpModel) - 1) {
                    try {
                        listRawData.add(dataClearInput[counter])
                        counter++
                    } catch (ex: Exception) {
                        aapsLogger.error(
                            LTag.PUMPBTCOMM, "OpCode: " + ByteUtil.shortHexString(opCode.toByte()) + ", Invalid package: "
                            + ByteUtil.getHex(listRawData))
                        // throw ex;
                        incompletePacket = true
                        break
                    }
                }
                if (incompletePacket) break
            }
            if (entryType === PumpHistoryEntryType.None) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Error in code. We should have not come into this branch.")
            } else {
                if (pe.entryType === PumpHistoryEntryType.UnknownBasePacket) {
                    pe.opCode = opCode.toByte()
                }
                if (entryType.getHeadLength(medtronicUtil.medtronicPumpModel) == 0) special = true
                pe.setData(listRawData, special)
                val decoded = decodeRecord(pe)
                if (decoded === RecordDecodeStatus.OK || decoded === RecordDecodeStatus.Ignored) {
                    //Log.i(TAG, "#" + record + " " + decoded.getDescription() + " " + pe);
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "#" + record + " " + decoded.description + "  " + pe)
                }
                addToStatistics(pe, decoded, null)
                record++
                if (decoded === RecordDecodeStatus.OK) // we add only OK records, all others are ignored
                {
                    outList.add(pe)
                }
            }
        } while (counter < dataClearInput.size)
        return outList
    }

    override fun decodeRecord(record: PumpHistoryEntry): RecordDecodeStatus {
        return try {
            decodeRecordInternal(record)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "     Error decoding: type=%s, ex=%s", record.entryType.name, ex.message, ex))
            //ex.printStackTrace()
            RecordDecodeStatus.Error
        }
    }

    private fun decodeRecordInternal(entry: PumpHistoryEntry): RecordDecodeStatus {
        if (entry.dateTimeLength > 0) {
            decodeDateTime(entry)
        }
        return when (entry.entryType) {
            PumpHistoryEntryType.ChangeBasalPattern,
            PumpHistoryEntryType.CalBGForPH,
            PumpHistoryEntryType.ChangeRemoteId,
            PumpHistoryEntryType.ClearAlarm,
            PumpHistoryEntryType.ChangeAlarmNotifyMode,
            PumpHistoryEntryType.EnableDisableRemote,
            PumpHistoryEntryType.BGReceived,
            PumpHistoryEntryType.SensorAlert,
            PumpHistoryEntryType.ChangeTimeFormat,
            PumpHistoryEntryType.ChangeReservoirWarningTime,
            PumpHistoryEntryType.ChangeBolusReminderEnable,
            PumpHistoryEntryType.SetBolusReminderTime,
            PumpHistoryEntryType.ChangeChildBlockEnable,
            PumpHistoryEntryType.BolusWizardEnabled,
            PumpHistoryEntryType.ChangeBGReminderOffset,
            PumpHistoryEntryType.ChangeAlarmClockTime,
            PumpHistoryEntryType.ChangeMeterId,
            PumpHistoryEntryType.ChangeParadigmID,
            PumpHistoryEntryType.JournalEntryMealMarker,
            PumpHistoryEntryType.JournalEntryExerciseMarker,
            PumpHistoryEntryType.DeleteBolusReminderTime,
            PumpHistoryEntryType.SetAutoOff,
            PumpHistoryEntryType.SelfTest,
            PumpHistoryEntryType.JournalEntryInsulinMarker,
            PumpHistoryEntryType.JournalEntryOtherMarker,
            PumpHistoryEntryType.BolusWizardSetup512,
            PumpHistoryEntryType.ChangeSensorSetup2,
            PumpHistoryEntryType.ChangeSensorAlarmSilenceConfig,
            PumpHistoryEntryType.ChangeSensorRateOfChangeAlertSetup,
            PumpHistoryEntryType.ChangeBolusScrollStepSize,
            PumpHistoryEntryType.BolusWizardSetup,
            PumpHistoryEntryType.ChangeVariableBolus,
            PumpHistoryEntryType.ChangeAudioBolus,
            PumpHistoryEntryType.ChangeBGReminderEnable,
            PumpHistoryEntryType.ChangeAlarmClockEnable,
            PumpHistoryEntryType.BolusReminder,
            PumpHistoryEntryType.DeleteAlarmClockTime,
            PumpHistoryEntryType.ChangeCarbUnits,
            PumpHistoryEntryType.ChangeWatchdogEnable,
            PumpHistoryEntryType.ChangeOtherDeviceID,
            PumpHistoryEntryType.ReadOtherDevicesIDs,
            PumpHistoryEntryType.BGReceived512,
            PumpHistoryEntryType.SensorStatus,
            PumpHistoryEntryType.ReadCaptureEventEnabled,
            PumpHistoryEntryType.ChangeCaptureEventEnable,
            PumpHistoryEntryType.ReadOtherDevicesStatus                       -> RecordDecodeStatus.OK

            PumpHistoryEntryType.Sensor_0x54,
            PumpHistoryEntryType.Sensor_0x55,
            PumpHistoryEntryType.Sensor_0x51,
            PumpHistoryEntryType.Sensor_0x52,
            PumpHistoryEntryType.EventUnknown_MM512_0x2e                      -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, " -- ignored Unknown Pump Entry: $entry")
                RecordDecodeStatus.Ignored
            }

            PumpHistoryEntryType.UnabsorbedInsulin,
            PumpHistoryEntryType.UnabsorbedInsulin512                         -> RecordDecodeStatus.Ignored
            PumpHistoryEntryType.DailyTotals522,
            PumpHistoryEntryType.DailyTotals523,
            PumpHistoryEntryType.DailyTotals515,
            PumpHistoryEntryType.EndResultTotals                              -> decodeDailyTotals(entry)
            PumpHistoryEntryType.ChangeBasalProfile_OldProfile,
            PumpHistoryEntryType.ChangeBasalProfile_NewProfile                -> decodeBasalProfile(entry)
            PumpHistoryEntryType.BasalProfileStart                            -> decodeBasalProfileStart(entry)

            PumpHistoryEntryType.ChangeTime                                   -> {
                changeTimeRecord = entry
                RecordDecodeStatus.OK
            }

            PumpHistoryEntryType.NewTimeSet                                   -> {
                decodeChangeTime(entry)
                RecordDecodeStatus.OK
            }

            PumpHistoryEntryType.TempBasalDuration                            ->   // decodeTempBasal(entry);
                RecordDecodeStatus.OK

            PumpHistoryEntryType.TempBasalRate                                ->  // decodeTempBasal(entry);
                RecordDecodeStatus.OK

            PumpHistoryEntryType.Bolus                                        -> {
                decodeBolus(entry)
                RecordDecodeStatus.OK
            }

            PumpHistoryEntryType.BatteryChange                                -> {
                decodeBatteryActivity(entry)
                RecordDecodeStatus.OK
            }

            PumpHistoryEntryType.LowReservoir                                 -> {
                decodeLowReservoir(entry)
                RecordDecodeStatus.OK
            }

            PumpHistoryEntryType.LowBattery,
            PumpHistoryEntryType.SuspendPump,
            PumpHistoryEntryType.ResumePump,
            PumpHistoryEntryType.Rewind,
            PumpHistoryEntryType.NoDeliveryAlarm,
            PumpHistoryEntryType.ChangeTempBasalType,
            PumpHistoryEntryType.ChangeMaxBolus,
            PumpHistoryEntryType.ChangeMaxBasal,
            PumpHistoryEntryType.ClearSettings,
            PumpHistoryEntryType.SaveSettings                                 -> RecordDecodeStatus.OK
            PumpHistoryEntryType.BolusWizard                                  -> decodeBolusWizard(entry)
            PumpHistoryEntryType.BolusWizard512                               -> decodeBolusWizard512(entry)

            PumpHistoryEntryType.Prime                                        -> {
                decodePrime(entry)
                RecordDecodeStatus.OK
            }

            PumpHistoryEntryType.TempBasalCombined                            -> RecordDecodeStatus.Ignored
            PumpHistoryEntryType.None, PumpHistoryEntryType.UnknownBasePacket -> RecordDecodeStatus.Error

            else                                                              -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Not supported: " + entry.entryType)
                RecordDecodeStatus.NotSupported
            }
        }
    }

    private fun decodeDailyTotals(entry: PumpHistoryEntry): RecordDecodeStatus {
        entry.addDecodedData("Raw Data", ByteUtil.getHex(entry.rawData))
        val totals = DailyTotalsDTO(entry)
        entry.addDecodedData("Object", totals)
        return RecordDecodeStatus.OK
    }

    private fun decodeBasalProfile(entry: PumpHistoryEntry): RecordDecodeStatus {
        val basalProfile = BasalProfile(aapsLogger)
        basalProfile.setRawDataFromHistory(entry.body)
        entry.addDecodedData("Object", basalProfile)
        return RecordDecodeStatus.OK
    }

    private fun decodeChangeTime(entry: PumpHistoryEntry) {
        if (changeTimeRecord == null) return
        entry.displayableValue = entry.dateTimeString
        changeTimeRecord = null
    }

    private fun decodeBatteryActivity(entry: PumpHistoryEntry) {
        entry.displayableValue = if (entry.head[0] == 0.toByte()) "Battery Removed" else "Battery Replaced"
    }

    private fun decodeBasalProfileStart(entry: PumpHistoryEntry): RecordDecodeStatus {
        val body = entry.body
        val offset = body[0] * 1000 * 30 * 60
        var rate: Float? = null
        val index = entry.head[0].toInt()
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_523andHigher)) {
            rate = body[1] * 0.025f
        }

        //LOG.info("Basal Profile Start: offset={}, rate={}, index={}, body_raw={}", offset, rate, index, body);
        return if (rate == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Basal Profile Start (ERROR): offset=%d, rate=%.3f, index=%d, body_raw=%s", offset, rate, index, ByteUtil.getHex(body)))
            RecordDecodeStatus.Error
        } else {
            entry.addDecodedData("Value", getFormattedFloat(rate, 3))
            entry.displayableValue = getFormattedFloat(rate, 3)
            RecordDecodeStatus.OK
        }
    }

    private fun decodeBolusWizard(entry: PumpHistoryEntry): RecordDecodeStatus {
        val body = entry.body
        val dto = BolusWizardDTO()
        var bolusStrokes = 10.0f
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_523andHigher)) {
            // https://github.com/ps2/minimed_rf/blob/master/lib/minimed_rf/log_entries/bolus_wizard.rb#L102
            bolusStrokes = 40.0f
            dto.carbs = ((body[1] and 0x0c.toByte()).toInt() shl 6) + body[0]
            dto.bloodGlucose = ((body[1] and 0x03).toInt() shl 8) + entry.head[0]
            dto.carbRatio = body[1] / 10.0f
            // carb_ratio (?) = (((self.body[2] & 0x07) << 8) + self.body[3]) /
            // 10.0s
            dto.insulinSensitivity = body[4].toFloat()
            dto.bgTargetLow = body[5].toInt()
            dto.bgTargetHigh = body[14].toInt()
            dto.correctionEstimate = (((body[9] and 0x38).toInt() shl 5) + body[6]) / bolusStrokes
            dto.foodEstimate = ((body[7].toInt() shl 8) + body[8]) / bolusStrokes
            dto.unabsorbedInsulin = ((body[10].toInt() shl 8) + body[11]) / bolusStrokes
            dto.bolusTotal = ((body[12].toInt() shl 8) + body[13]) / bolusStrokes
        } else {
            dto.bloodGlucose = (body.get(1) and 0x0F).toInt() shl 8 or entry.head.get(0).toInt()
            dto.carbs = body[0].toInt()
            dto.carbRatio = body[2].toFloat()
            dto.insulinSensitivity = body[3].toFloat()
            dto.bgTargetLow = body.get(4).toInt()
            dto.bgTargetHigh = body.get(12).toInt()
            dto.bolusTotal = body.get(11) / bolusStrokes
            dto.foodEstimate = body.get(6) / bolusStrokes
            dto.unabsorbedInsulin = body.get(9) / bolusStrokes
            dto.bolusTotal = body.get(11) / bolusStrokes
            dto.correctionEstimate = (body.get(7) + (body.get(5) and 0x0F)) / bolusStrokes
        }
        if (dto.bloodGlucose < 0) {
            dto.bloodGlucose = ByteUtil.convertUnsignedByteToInt(dto.bloodGlucose.toByte())
        }
        dto.atechDateTime = entry.atechDateTime
        entry.addDecodedData("Object", dto)
        entry.displayableValue = dto.displayableValue
        return RecordDecodeStatus.OK
    }

    private fun decodeBolusWizard512(entry: PumpHistoryEntry): RecordDecodeStatus {
        val body = entry.body
        val dto = BolusWizardDTO()
        val bolusStrokes = 10.0f
        dto.bloodGlucose = (body.get(1) and 0x03).toInt() shl 8 or entry.head.get(0).toInt()
        dto.carbs = body.get(1).toInt() and 0xC shl 6 or body.get(0).toInt() // (int)body[0];
        dto.carbRatio = body.get(2).toFloat()
        dto.insulinSensitivity = body.get(3).toFloat()
        dto.bgTargetLow = body.get(4).toInt()
        dto.foodEstimate = body.get(6) / 10.0f
        dto.correctionEstimate = (body.get(7) + (body.get(5) and 0x0F)) / bolusStrokes
        dto.unabsorbedInsulin = body.get(9) / bolusStrokes
        dto.bolusTotal = body.get(11) / bolusStrokes
        dto.bgTargetHigh = dto.bgTargetLow
        if (dto.bloodGlucose < 0) {
            dto.bloodGlucose = ByteUtil.convertUnsignedByteToInt(dto.bloodGlucose.toByte())
        }
        dto.atechDateTime = entry.atechDateTime
        entry.addDecodedData("Object", dto)
        entry.displayableValue = dto.displayableValue
        return RecordDecodeStatus.OK
    }

    private fun decodeLowReservoir(entry: PumpHistoryEntry) {
        val amount = getUnsignedInt(entry.head.get(0)) * 1.0f / 10.0f * 2
        entry.displayableValue = getFormattedValue(amount, 1)
    }

    private fun decodePrime(entry: PumpHistoryEntry) {
        val amount = ByteUtil.toInt(entry.head.get(2), entry.head.get(3)) / 10.0f
        val fixed = ByteUtil.toInt(entry.head.get(0), entry.head.get(1)) / 10.0f

//        amount = (double)(asUINT8(data[4]) << 2) / 40.0;
//        programmedAmount = (double)(asUINT8(data[2]) << 2) / 40.0;
//        primeType = programmedAmount == 0 ? "manual" : "fixed";
        entry.addDecodedData("Amount", amount)
        entry.addDecodedData("FixedAmount", fixed)
        entry.displayableValue = ("Amount=" + getFormattedValue(amount, 2) + ", Fixed Amount="
            + getFormattedValue(fixed, 2))
    }

    private fun decodeChangeTempBasalType(entry: PumpHistoryEntry) {
        entry.addDecodedData("isPercent", ByteUtil.asUINT8(entry.getRawDataByIndex(0)) == 1) // index moved from 1 -> 0
    }

    private fun decodeBgReceived(entry: PumpHistoryEntry) {
        entry.addDecodedData("amount", (ByteUtil.asUINT8(entry.getRawDataByIndex(0)) shl 3) + (ByteUtil.asUINT8(entry.getRawDataByIndex(3)) shr 5))
        entry.addDecodedData("meter", ByteUtil.substring(entry.rawData, 6, 3)) // index moved from 1 -> 0
    }

    private fun decodeCalBGForPH(entry: PumpHistoryEntry) {
        entry.addDecodedData("amount", (ByteUtil.asUINT8(entry.getRawDataByIndex(5)) and 0x80 shl 1) + ByteUtil.asUINT8(entry.getRawDataByIndex(0))) // index moved from 1 -> 0
    }

    // private fun decodeNoDeliveryAlarm(entry: PumpHistoryEntry) {
    //     //rawtype = asUINT8(data[1]);
    //     // not sure if this is actually NoDelivery Alarm?
    // }

    override fun postProcess() {}

    override fun runPostDecodeTasks() {
        showStatistics()
    }

    private fun decodeBolus(entry: PumpHistoryEntry) {
        val bolus: BolusDTO?
        val data = entry.head
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_523andHigher)) {
            bolus = BolusDTO(atechDateTime = entry.atechDateTime,
                             requestedAmount = ByteUtil.toInt(data.get(0), data.get(1)) / 40.0,
                             deliveredAmount = ByteUtil.toInt(data.get(2), data.get(3)) / 40.0,
                             duration = data.get(6) * 30)
            bolus.insulinOnBoard = ByteUtil.toInt(data.get(4), data.get(5)) / 40.0
        } else {
            bolus = BolusDTO(atechDateTime = entry.atechDateTime,
                             requestedAmount = ByteUtil.asUINT8(data.get(0)) / 10.0,
                             deliveredAmount = ByteUtil.asUINT8(data.get(1)) / 10.0,
                             duration = ByteUtil.asUINT8(data.get(2)) * 30)
        }
        bolus.bolusType = if (bolus.duration > 0) PumpBolusType.Extended else PumpBolusType.Normal
        entry.addDecodedData("Object", bolus)
        entry.displayableValue = bolus.displayableValue
    }

    fun decodeTempBasal(tbrPreviousRecord: PumpHistoryEntry, entry: PumpHistoryEntry) {
        var tbrRate: PumpHistoryEntry? = null
        var tbrDuration: PumpHistoryEntry? = null
        if (entry.entryType === PumpHistoryEntryType.TempBasalRate) {
            tbrRate = entry
        } else {
            tbrDuration = entry
        }
        if (tbrRate != null) {
            tbrDuration = tbrPreviousRecord
        } else {
            tbrRate = tbrPreviousRecord
        }

        val tbr = TempBasalPair(
            tbrRate.head.get(0),
            tbrRate.body.get(0),
            tbrDuration!!.head.get(0).toInt(),
            ByteUtil.asUINT8(tbrRate.datetime.get(4)) shr 3 == 0)

        entry.addDecodedData("Object", tbr)
        entry.displayableValue = tbr.description
    }

    private fun decodeDateTime(entry: PumpHistoryEntry) {
        if (entry.datetime.size == 0) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "DateTime not set.")
        }
        val dt = entry.datetime
        if (entry.dateTimeLength == 5) {
            val seconds: Int = (dt.get(0) and 0x3F.toByte()).toInt()
            val minutes: Int = (dt.get(1) and 0x3F.toByte()).toInt()
            val hour: Int = (dt.get(2) and 0x1F).toInt()
            val month: Int = (dt.get(0).toInt() shr 4 and 0x0c) + (dt.get(1).toInt() shr 6 and 0x03)
            // ((dt[0] & 0xC0) >> 6) | ((dt[1] & 0xC0) >> 4);
            val dayOfMonth: Int = (dt.get(3) and 0x1F).toInt()
            val year = fix2DigitYear((dt.get(4) and 0x3F.toByte()).toInt()) // Assuming this is correct, need to verify. Otherwise this will be
            // a problem in 2016.
            entry.atechDateTime = DateTimeUtil.toATechDate(year, month, dayOfMonth, hour, minutes, seconds)
        } else if (entry.dateTimeLength == 2) {
            //val low = ByteUtil.asUINT8(dt.get(0)) and 0x1F
            val mhigh = ByteUtil.asUINT8(dt.get(0)) and 0xE0 shr 4
            val mlow = ByteUtil.asUINT8(dt.get(1)) and 0x80 shr 7
            val month = mhigh + mlow
            // int dayOfMonth = low + 1;
            val dayOfMonth: Int = (dt.get(0) and 0x1F).toInt()
            val year = 2000 + (ByteUtil.asUINT8(dt.get(1)) and 0x7F)
            var hour = 0
            var minutes = 0
            var seconds = 0

            //LOG.debug("DT: {} {} {}", year, month, dayOfMonth);
            if (dayOfMonth == 32) {
                aapsLogger.warn(
                    LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Entry: Day 32 %s = [%s] %s", entry.entryType.name,
                                                   ByteUtil.getHex(entry.rawData), entry))
            }
            if (isEndResults(entry.entryType)) {
                hour = 23
                minutes = 59
                seconds = 59
            }
            entry.atechDateTime = DateTimeUtil.toATechDate(year, month, dayOfMonth, hour, minutes, seconds)
        } else {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Unknown datetime format: " + entry.dateTimeLength)
        }
    }

    private fun isEndResults(entryType: PumpHistoryEntryType?): Boolean {
        return entryType === PumpHistoryEntryType.EndResultTotals ||
            entryType === PumpHistoryEntryType.DailyTotals515 ||
            entryType === PumpHistoryEntryType.DailyTotals522 ||
            entryType === PumpHistoryEntryType.DailyTotals523
    }

    private fun fix2DigitYear(year: Int): Int {
        var yearInternal = year
        yearInternal += if (yearInternal > 90) {
            1900
        } else {
            2000
        }
        return yearInternal
    }

    companion object {

        private fun getFormattedValue(value: Float, decimals: Int): String {
            return String.format(Locale.ENGLISH, "%." + decimals + "f", value)
        }
    }

}