package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RecordDecodeStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusWizardDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.DailyTotalsDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpBolusType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 * <p>
 * Author: Andy {andy.rozman@gmail.com}
 */

@Singleton
public class MedtronicPumpHistoryDecoder extends MedtronicHistoryDecoder<PumpHistoryEntry> {

    private final AAPSLogger aapsLogger;
    private final MedtronicUtil medtronicUtil;

    private PumpHistoryEntry tbrPreviousRecord;
    private PumpHistoryEntry changeTimeRecord;

    @Inject
    public MedtronicPumpHistoryDecoder(
            AAPSLogger aapsLogger,
            MedtronicUtil medtronicUtil
    ) {
        this.aapsLogger = aapsLogger;
        this.medtronicUtil = medtronicUtil;
    }


    public List<PumpHistoryEntry> createRecords(List<Byte> dataClear) {
        prepareStatistics();

        int counter = 0;
        int record = 0;
        boolean incompletePacket;

        List<PumpHistoryEntry> outList = new ArrayList<>();
        String skipped = null;

        if (dataClear.size() == 0) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Empty page.");
            return outList;
        }

        do {
            int opCode = dataClear.get(counter);
            boolean special = false;
            incompletePacket = false;

            if (opCode == 0) {
                counter++;
                if (skipped == null)
                    skipped = "0x00";
                else
                    skipped += " 0x00";
                continue;
            } else {
                if (skipped != null) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, " ... Skipped " + skipped);
                    skipped = null;
                }
            }

            PumpHistoryEntryType entryType = PumpHistoryEntryType.getByCode(opCode);

            PumpHistoryEntry pe = new PumpHistoryEntry();
            pe.setEntryType(medtronicUtil.getMedtronicPumpModel(), entryType);
            pe.setOffset(counter);

            counter++;

            if (counter >= 1022) {
                break;
            }

            List<Byte> listRawData = new ArrayList<>();
            listRawData.add((byte) opCode);

            if (entryType == PumpHistoryEntryType.UnabsorbedInsulin
                    || entryType == PumpHistoryEntryType.UnabsorbedInsulin512) {
                int elements = dataClear.get(counter);
                listRawData.add((byte) elements);
                counter++;

                int els = getUnsignedInt(elements);

                for (int k = 0; k < (els - 2); k++) {
                    if (counter<1022) {
                        listRawData.add(dataClear.get(counter));
                        counter++;
                    }
                }

                special = true;
            } else {

                for (int j = 0; j < (entryType.getTotalLength(medtronicUtil.getMedtronicPumpModel()) - 1); j++) {

                    try {
                        listRawData.add(dataClear.get(counter));
                        counter++;
                    } catch (Exception ex) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "OpCode: " + ByteUtil.shortHexString((byte) opCode) + ", Invalid package: "
                                + ByteUtil.getHex(listRawData));
                        // throw ex;
                        incompletePacket = true;
                        break;
                    }

                }

                if (incompletePacket)
                    break;

            }

            if (entryType == PumpHistoryEntryType.None) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Error in code. We should have not come into this branch.");
            } else {

                if (pe.getEntryType() == PumpHistoryEntryType.UnknownBasePacket) {
                    pe.setOpCode(opCode);
                }

                if (entryType.getHeadLength(medtronicUtil.getMedtronicPumpModel()) == 0)
                    special = true;

                pe.setData(listRawData, special);

                RecordDecodeStatus decoded = decodeRecord(pe);

                if ((decoded == RecordDecodeStatus.OK) || (decoded == RecordDecodeStatus.Ignored)) {
                    //Log.i(TAG, "#" + record + " " + decoded.getDescription() + " " + pe);
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "#" + record + " " + decoded.getDescription() + "  " + pe);
                }

                addToStatistics(pe, decoded, null);

                record++;

                if (decoded == RecordDecodeStatus.OK) // we add only OK records, all others are ignored
                {
                    outList.add(pe);
                }
            }

        } while (counter < dataClear.size());

        return outList;
    }


    public RecordDecodeStatus decodeRecord(PumpHistoryEntry record) {
        try {
            return decodeRecord(record, false);
        } catch (Exception ex) {
            aapsLogger.error(LTag.PUMPBTCOMM, "     Error decoding: type={}, ex={}", record.getEntryType().name(), ex.getMessage(), ex);
            return RecordDecodeStatus.Error;
        }
    }


    private RecordDecodeStatus decodeRecord(PumpHistoryEntry entry, boolean x) {

        if (entry.getDateTimeLength() > 0) {
            decodeDateTime(entry);
        }

        switch (entry.getEntryType()) {

            // Valid entries, but not processed
            case ChangeBasalPattern:
            case CalBGForPH:
            case ChangeRemoteId:
            case ClearAlarm:
            case ChangeAlarmNotifyMode: // ChangeUtility:
            case EnableDisableRemote:
            case BGReceived: // Ian3F: CGMS
            case SensorAlert: // Ian08 CGMS
            case ChangeTimeFormat:
            case ChangeReservoirWarningTime:
            case ChangeBolusReminderEnable:
            case ChangeBolusReminderTime:
            case ChangeChildBlockEnable:
            case BolusWizardEnabled:
            case ChangeBGReminderOffset:
            case ChangeAlarmClockTime:
            case ChangeMeterId:
            case ChangeParadigmID:
            case JournalEntryMealMarker:
            case JournalEntryExerciseMarker:
            case DeleteBolusReminderTime:
            case SetAutoOff:
            case SelfTest:
            case JournalEntryInsulinMarker:
            case JournalEntryOtherMarker:
            case ChangeBolusWizardSetup512:
            case ChangeSensorSetup2:
            case ChangeSensorAlarmSilenceConfig:
            case ChangeSensorRateOfChangeAlertSetup:
            case ChangeBolusScrollStepSize:
            case ChangeBolusWizardSetup:
            case ChangeVariableBolus:
            case ChangeAudioBolus:
            case ChangeBGReminderEnable:
            case ChangeAlarmClockEnable:
            case BolusReminder:
            case DeleteAlarmClockTime:
            case ChangeCarbUnits:
            case ChangeWatchdogEnable:
            case ChangeOtherDeviceID:
            case ReadOtherDevicesIDs:
            case BGReceived512:
            case SensorStatus:
            case ReadCaptureEventEnabled:
            case ChangeCaptureEventEnable:
            case ReadOtherDevicesStatus:
                return RecordDecodeStatus.OK;

            case Sensor_0x54:
            case Sensor_0x55:
            case Sensor_0x51:
            case Sensor_0x52:
            case EventUnknown_MM522_0x45:
            case EventUnknown_MM522_0x46:
            case EventUnknown_MM522_0x47:
            case EventUnknown_MM522_0x48:
            case EventUnknown_MM522_0x49:
            case EventUnknown_MM522_0x4a:
            case EventUnknown_MM522_0x4b:
            case EventUnknown_MM522_0x4c:
            case EventUnknown_MM512_0x10:
            case EventUnknown_MM512_0x2e:
            case EventUnknown_MM512_0x37:
            case EventUnknown_MM512_0x38:
            case EventUnknown_MM512_0x4e:
            case EventUnknown_MM522_0x70:
            case EventUnknown_MM512_0x88:
            case EventUnknown_MM512_0x94:
            case EventUnknown_MM522_0xE8:
            case EventUnknown_0x4d:
            case EventUnknown_MM522_0x25:
            case EventUnknown_MM522_0x05:
                aapsLogger.debug(LTag.PUMPBTCOMM, " -- ignored Unknown Pump Entry: " + entry);
                return RecordDecodeStatus.Ignored;

            case UnabsorbedInsulin:
            case UnabsorbedInsulin512:
                return RecordDecodeStatus.Ignored;

            // **** Implemented records ****

            case DailyTotals522:
            case DailyTotals523:
            case DailyTotals515:
            case EndResultTotals:
                return decodeDailyTotals(entry);

            case ChangeBasalProfile_OldProfile:
            case ChangeBasalProfile_NewProfile:
                return decodeBasalProfile(entry);

            case BasalProfileStart:
                return decodeBasalProfileStart(entry);

            case ChangeTime:
                changeTimeRecord = entry;
                return RecordDecodeStatus.OK;

            case NewTimeSet:
                decodeChangeTime(entry);
                return RecordDecodeStatus.OK;

            case TempBasalDuration:
                // decodeTempBasal(entry);
                return RecordDecodeStatus.OK;

            case TempBasalRate:
                // decodeTempBasal(entry);
                return RecordDecodeStatus.OK;

            case Bolus:
                decodeBolus(entry);
                return RecordDecodeStatus.OK;

            case BatteryChange:
                decodeBatteryActivity(entry);
                return RecordDecodeStatus.OK;

            case LowReservoir:
                decodeLowReservoir(entry);
                return RecordDecodeStatus.OK;

            case LowBattery:
            case Suspend:
            case Resume:
            case Rewind:
            case NoDeliveryAlarm:
            case ChangeTempBasalType:
            case ChangeMaxBolus:
            case ChangeMaxBasal:
            case ClearSettings:
            case SaveSettings:
                return RecordDecodeStatus.OK;

            case BolusWizard:
                return decodeBolusWizard(entry);

            case BolusWizard512:
                return decodeBolusWizard512(entry);

            case Prime:
                decodePrime(entry);
                return RecordDecodeStatus.OK;

            case TempBasalCombined:
                return RecordDecodeStatus.Ignored;

            case None:
            case UnknownBasePacket:
                return RecordDecodeStatus.Error;

            default: {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Not supported: " + entry.getEntryType());
                return RecordDecodeStatus.NotSupported;
            }

        }

        // return RecordDecodeStatus.Error;

    }


    private RecordDecodeStatus decodeDailyTotals(PumpHistoryEntry entry) {

        entry.addDecodedData("Raw Data", ByteUtil.getHex(entry.getRawData()));

        DailyTotalsDTO totals = new DailyTotalsDTO(entry);

        entry.addDecodedData("Object", totals);

        return RecordDecodeStatus.OK;
    }


    private RecordDecodeStatus decodeBasalProfile(PumpHistoryEntry entry) {

        // LOG.debug("decodeBasalProfile: {}", entry);

        BasalProfile basalProfile = new BasalProfile(aapsLogger);
        basalProfile.setRawDataFromHistory(entry.getBody());

        // LOG.debug("decodeBasalProfile BasalProfile: {}", basalProfile);

        entry.addDecodedData("Object", basalProfile);

        return RecordDecodeStatus.OK;
    }


    private void decodeChangeTime(PumpHistoryEntry entry) {
        if (changeTimeRecord == null)
            return;

        entry.setDisplayableValue(entry.getDateTimeString());

        this.changeTimeRecord = null;
    }


    private void decodeBatteryActivity(PumpHistoryEntry entry) {
        // this.writeData(PumpBaseType.Event, entry.getHead()[0] == 0 ? PumpEventType.BatteryRemoved :
        // PumpEventType.BatteryReplaced, entry.getATechDate());

        entry.setDisplayableValue(entry.getHead()[0] == 0 ? "Battery Removed" : "Battery Replaced");
    }


    private static String getFormattedValue(float value, int decimals) {
        return String.format(Locale.ENGLISH, "%." + decimals + "f", value);
    }


    private RecordDecodeStatus decodeBasalProfileStart(PumpHistoryEntry entry) {
        byte[] body = entry.getBody();
        // int bodyOffset = headerSize + timestampSize;
        int offset = body[0] * 1000 * 30 * 60;
        Float rate = null;
        int index = entry.getHead()[0];

        if (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_523andHigher)) {
            rate = body[1] * 0.025f;
        }

        //LOG.info("Basal Profile Start: offset={}, rate={}, index={}, body_raw={}", offset, rate, index, body);

        if (rate == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Basal Profile Start (ERROR): offset={}, rate={}, index={}, body_raw={}", offset, rate, index, body);
            return RecordDecodeStatus.Error;
        } else {
            entry.addDecodedData("Value", getFormattedFloat(rate, 3));
            entry.setDisplayableValue(getFormattedFloat(rate, 3));
            return RecordDecodeStatus.OK;
        }

    }


    private RecordDecodeStatus decodeBolusWizard(PumpHistoryEntry entry) {
        byte[] body = entry.getBody();

        BolusWizardDTO dto = new BolusWizardDTO();

        float bolusStrokes = 10.0f;

        if (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_523andHigher)) {
            // https://github.com/ps2/minimed_rf/blob/master/lib/minimed_rf/log_entries/bolus_wizard.rb#L102
            bolusStrokes = 40.0f;

            dto.carbs = ((body[1] & 0x0c) << 6) + body[0];

            dto.bloodGlucose = ((body[1] & 0x03) << 8) + entry.getHead()[0];
            dto.carbRatio = body[1] / 10.0f;
            // carb_ratio (?) = (((self.body[2] & 0x07) << 8) + self.body[3]) /
            // 10.0s
            dto.insulinSensitivity = new Float(body[4]);
            dto.bgTargetLow = (int) body[5];
            dto.bgTargetHigh = (int) body[14];
            dto.correctionEstimate = (((body[9] & 0x38) << 5) + body[6]) / bolusStrokes;
            dto.foodEstimate = ((body[7] << 8) + body[8]) / bolusStrokes;
            dto.unabsorbedInsulin = ((body[10] << 8) + body[11]) / bolusStrokes;
            dto.bolusTotal = ((body[12] << 8) + body[13]) / bolusStrokes;
        } else {
            dto.bloodGlucose = (((body[1] & 0x0F) << 8) | entry.getHead()[0]);
            dto.carbs = (int) body[0];
            dto.carbRatio = Float.valueOf(body[2]);
            dto.insulinSensitivity = new Float(body[3]);
            dto.bgTargetLow = (int) body[4];
            dto.bgTargetHigh = (int) body[12];
            dto.bolusTotal = body[11] / bolusStrokes;
            dto.foodEstimate = body[6] / bolusStrokes;
            dto.unabsorbedInsulin = body[9] / bolusStrokes;
            dto.bolusTotal = body[11] / bolusStrokes;
            dto.correctionEstimate = (body[7] + (body[5] & 0x0F)) / bolusStrokes;
        }

        if (dto.bloodGlucose != null && dto.bloodGlucose < 0) {
            dto.bloodGlucose = ByteUtil.convertUnsignedByteToInt(dto.bloodGlucose.byteValue());
        }

        dto.atechDateTime = entry.atechDateTime;
        entry.addDecodedData("Object", dto);
        entry.setDisplayableValue(dto.getDisplayableValue());

        return RecordDecodeStatus.OK;
    }


    private RecordDecodeStatus decodeBolusWizard512(PumpHistoryEntry entry) {
        byte[] body = entry.getBody();

        BolusWizardDTO dto = new BolusWizardDTO();

        float bolusStrokes = 10.0f;

        dto.bloodGlucose = ((body[1] & 0x03 << 8) | entry.getHead()[0]);
        dto.carbs = (body[1] & 0xC) << 6 | body[0]; // (int)body[0];
        dto.carbRatio = Float.valueOf(body[2]);
        dto.insulinSensitivity = new Float(body[3]);
        dto.bgTargetLow = (int) body[4];
        dto.foodEstimate = body[6] / 10.0f;
        dto.correctionEstimate = (body[7] + (body[5] & 0x0F)) / bolusStrokes;
        dto.unabsorbedInsulin = body[9] / bolusStrokes;
        dto.bolusTotal = body[11] / bolusStrokes;
        dto.bgTargetHigh = dto.bgTargetLow;

        if (dto.bloodGlucose != null && dto.bloodGlucose < 0) {
            dto.bloodGlucose = ByteUtil.convertUnsignedByteToInt(dto.bloodGlucose.byteValue());
        }

        dto.atechDateTime = entry.atechDateTime;
        entry.addDecodedData("Object", dto);
        entry.setDisplayableValue(dto.getDisplayableValue());

        return RecordDecodeStatus.OK;
    }


    private void decodeLowReservoir(PumpHistoryEntry entry) {
        float amount = (getUnsignedInt(entry.getHead()[0]) * 1.0f / 10.0f) * 2;

        entry.setDisplayableValue(getFormattedValue(amount, 1));
    }


    private void decodePrime(PumpHistoryEntry entry) {
        float amount = ByteUtil.toInt(entry.getHead()[2], entry.getHead()[3]) / 10.0f;
        float fixed = ByteUtil.toInt(entry.getHead()[0], entry.getHead()[1]) / 10.0f;

//        amount = (double)(asUINT8(data[4]) << 2) / 40.0;
//        programmedAmount = (double)(asUINT8(data[2]) << 2) / 40.0;
//        primeType = programmedAmount == 0 ? "manual" : "fixed";

        entry.addDecodedData("Amount", amount);
        entry.addDecodedData("FixedAmount", fixed);

        entry.setDisplayableValue("Amount=" + getFormattedValue(amount, 2) + ", Fixed Amount="
                + getFormattedValue(fixed, 2));
    }


    private void decodeChangeTempBasalType(PumpHistoryEntry entry) {
        entry.addDecodedData("isPercent", ByteUtil.asUINT8(entry.getRawDataByIndex(0)) == 1); // index moved from 1 -> 0
    }


    private void decodeBgReceived(PumpHistoryEntry entry) {
        entry.addDecodedData("amount", (ByteUtil.asUINT8(entry.getRawDataByIndex(0)) << 3) + (ByteUtil.asUINT8(entry.getRawDataByIndex(3)) >> 5));
        entry.addDecodedData("meter", ByteUtil.substring(entry.getRawData(), 6, 3)); // index moved from 1 -> 0
    }


    private void decodeCalBGForPH(PumpHistoryEntry entry) {
        entry.addDecodedData("amount", ((ByteUtil.asUINT8(entry.getRawDataByIndex(5)) & 0x80) << 1) + ByteUtil.asUINT8(entry.getRawDataByIndex(0))); // index moved from 1 -> 0
    }


    private void decodeNoDeliveryAlarm(PumpHistoryEntry entry) {
        //rawtype = asUINT8(data[1]);
        // not sure if this is actually NoDelivery Alarm?
    }


    @Override
    public void postProcess() {
    }


    @Override
    protected void runPostDecodeTasks() {
        this.showStatistics();
    }


    private void decodeBolus(PumpHistoryEntry entry) {
        BolusDTO bolus = new BolusDTO();

        byte[] data = entry.getHead();

        if (MedtronicDeviceType.isSameDevice(medtronicUtil.getMedtronicPumpModel(), MedtronicDeviceType.Medtronic_523andHigher)) {
            bolus.setRequestedAmount(ByteUtil.toInt(data[0], data[1]) / 40.0d);
            bolus.setDeliveredAmount(ByteUtil.toInt(data[2], data[3]) / 40.0d);
            bolus.setInsulinOnBoard(ByteUtil.toInt(data[4], data[5]) / 40.0d);
            bolus.setDuration(data[6] * 30);
        } else {
            bolus.setRequestedAmount(ByteUtil.asUINT8(data[0]) / 10.0d);
            bolus.setDeliveredAmount(ByteUtil.asUINT8(data[1]) / 10.0d);
            bolus.setDuration(ByteUtil.asUINT8(data[2]) * 30);
        }

        bolus.setBolusType((bolus.getDuration() != null && (bolus.getDuration() > 0)) ? PumpBolusType.Extended
                : PumpBolusType.Normal);
        bolus.setAtechDateTime(entry.atechDateTime);

        entry.addDecodedData("Object", bolus);
        entry.setDisplayableValue(bolus.getDisplayableValue());

    }


    private void decodeTempBasal(PumpHistoryEntry entry) {

        if (this.tbrPreviousRecord == null) {
            // LOG.debug(this.tbrPreviousRecord.toString());
            this.tbrPreviousRecord = entry;
            return;
        }

        decodeTempBasal(this.tbrPreviousRecord, entry);

        tbrPreviousRecord = null;
    }


    public void decodeTempBasal(PumpHistoryEntry tbrPreviousRecord, PumpHistoryEntry entry) {

        PumpHistoryEntry tbrRate = null, tbrDuration = null;

        if (entry.getEntryType() == PumpHistoryEntryType.TempBasalRate) {
            tbrRate = entry;
        } else {
            tbrDuration = entry;
        }

        if (tbrRate != null) {
            tbrDuration = tbrPreviousRecord;
        } else {
            tbrRate = tbrPreviousRecord;
        }

        TempBasalPair tbr = new TempBasalPair(tbrRate.getHead()[0], tbrDuration.getHead()[0], (ByteUtil.asUINT8(tbrRate
                .getDatetime()[4]) >> 3) == 0);

        // System.out.println("TBR: amount=" + tbr.getInsulinRate() + ", duration=" + tbr.getDurationMinutes()
        // // + " min. Packed: " + tbr.getValue()
        // );

        entry.addDecodedData("Object", tbr);
        entry.setDisplayableValue(tbr.getDescription());

    }


    private void decodeDateTime(PumpHistoryEntry entry) {
        byte[] dt = entry.getDatetime();

        if (dt == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "DateTime not set.");
        }

        if (entry.getDateTimeLength() == 5) {

            int seconds = dt[0] & 0x3F;
            int minutes = dt[1] & 0x3F;
            int hour = dt[2] & 0x1F;

            int month = ((dt[0] >> 4) & 0x0c) + ((dt[1] >> 6) & 0x03);
            // ((dt[0] & 0xC0) >> 6) | ((dt[1] & 0xC0) >> 4);

            int dayOfMonth = dt[3] & 0x1F;
            int year = fix2DigitYear(dt[4] & 0x3F); // Assuming this is correct, need to verify. Otherwise this will be
            // a problem in 2016.

            entry.setAtechDateTime(DateTimeUtil.toATechDate(year, month, dayOfMonth, hour, minutes, seconds));

        } else if (entry.getDateTimeLength() == 2) {
            int low = ByteUtil.asUINT8(dt[0]) & 0x1F;
            int mhigh = (ByteUtil.asUINT8(dt[0]) & 0xE0) >> 4;
            int mlow = (ByteUtil.asUINT8(dt[1]) & 0x80) >> 7;
            int month = mhigh + mlow;
            // int dayOfMonth = low + 1;
            int dayOfMonth = dt[0] & 0x1F;
            int year = 2000 + (ByteUtil.asUINT8(dt[1]) & 0x7F);

            int hour = 0;
            int minutes = 0;
            int seconds = 0;

            //LOG.debug("DT: {} {} {}", year, month, dayOfMonth);

            if (dayOfMonth == 32) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Entry: Day 32 {} = [{}] {}", entry.getEntryType().name(),
                        ByteUtil.getHex(entry.getRawData()), entry);
            }

            if (isEndResults(entry.getEntryType())) {
                hour = 23;
                minutes = 59;
                seconds = 59;
            }

            entry.setAtechDateTime(DateTimeUtil.toATechDate(year, month, dayOfMonth, hour, minutes, seconds));

        } else {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Unknown datetime format: " + entry.getDateTimeLength());
        }

    }


    private boolean isEndResults(PumpHistoryEntryType entryType) {

        return (entryType == PumpHistoryEntryType.EndResultTotals ||
                entryType == PumpHistoryEntryType.DailyTotals515 ||
                entryType == PumpHistoryEntryType.DailyTotals522 ||
                entryType == PumpHistoryEntryType.DailyTotals523);
    }


    private int fix2DigitYear(int year) {
        if (year > 90) {
            year += 1900;
        } else {
            year += 2000;
        }

        return year;
    }

}
