package info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.util.Log;

import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.HexDump;
import info.nightscout.androidaps.plugins.PumpCommon.utils.StringUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.MedtronicHistoryDecoder;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.MedtronicHistoryEntry;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.RawHistoryPage;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.RecordDecodeStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BolusDTO;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BolusWizardDTO;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.PumpBolusType;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Application: GGC - GNU Gluco Control
 * Plug-in: GGC PlugIn Base (base class for all plugins)
 * <p>
 * See AUTHORS for copyright information.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * <p>
 * Filename: MedtronicPumpHistoryDecoder Description: Decoder for history data.
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public class MedtronicPumpHistoryDecoder extends MedtronicHistoryDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpHistoryDecoder.class);
    private static final String TAG = "MdtPump";

    // PumpValuesWriter pumpValuesWriter = null;

    // DataAccessPlugInBase dataAccess = DataAccessPump.getInstance();
    Map<String, BolusDTO> bolusHistory = new HashMap<>();
    // Temporary records for processing
    private PumpHistoryEntry tbrPreviousRecord;
    private PumpHistoryEntry changeTimeRecord;
    private MedtronicDeviceType deviceType;


    public MedtronicPumpHistoryDecoder() {

    }


    public List<? extends MedtronicHistoryEntry> processPageAndCreateRecords(RawHistoryPage page) {
        List<Byte> dataClear = checkPage(page);
        return createRecords(dataClear);
    }


    public List<? extends MedtronicHistoryEntry> createRecords(List<Byte> dataClear) {
        prepareStatistics();

        int counter = 0;
        int record = 0;
        boolean incompletePacket = false;
        deviceType = MedtronicUtil.getMedtronicPumpModel();

        List<MedtronicHistoryEntry> outList = new ArrayList<MedtronicHistoryEntry>();
        String skipped = null;
        int elementStart = 0;

        if (dataClear.size() == 0) {
            Log.e(TAG, "Empty page.");
            // return;
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
                    Log.v(TAG, " ... Skipped " + skipped);
                    skipped = null;
                }
            }

            PumpHistoryEntryType entryType = PumpHistoryEntryType.getByCode(opCode);

            PumpHistoryEntry pe = new PumpHistoryEntry();
            pe.setEntryType(entryType);
            pe.setOffset(counter);

            counter++;

            if (counter >= 1022) {
                break;
            }

            List<Byte> listRawData = new ArrayList<Byte>();
            listRawData.add((byte)opCode);

            if (entryType == PumpHistoryEntryType.UnabsorbedInsulin) {
                int elements = dataClear.get(counter);
                listRawData.add((byte)elements);
                counter++;

                int els = getUnsignedInt(elements);

                for (int k = 0; k < (els - 2); k++) {
                    listRawData.add((byte)dataClear.get(counter));
                    counter++;
                }

                special = true;
            } else {

                for (int j = 0; j < (entryType.getTotalLength() - 1); j++) {

                    try {
                        listRawData.add(dataClear.get(counter));
                        counter++;
                    } catch (Exception ex) {
                        Log.e(TAG, "OpCode: " + HexDump.getCorrectHexValue((byte)opCode) + ", Invalid package: "
                            + HexDump.toHexStringDisplayable(listRawData));
                        // throw ex;
                        incompletePacket = true;
                        break;
                    }

                }

                if (incompletePacket)
                    break;

            }

            if (entryType == PumpHistoryEntryType.None) {
                LOG.error("Error in code. We should have not come into this branch.");
                // System.out.println("!!! Unknown Entry: 0x" +
                // bitUtils.getCorrectHexValue(opCode) + "[" + opCode + "]");
                //
                // addToStatistics(null, null, opCode);
                // counter += 6; // we assume this is unknown packet with size
                // // 2,5,0 (standard packet)
                //
                // pe.setEntryType(PumpHistoryEntryType.UnknownBasePacket);
                // pe.setOpCode(opCode);

            } else {

                // System.out.println(pe.getEntryType());

                if (pe.getEntryType() == PumpHistoryEntryType.UnknownBasePacket) {
                    pe.setOpCode(opCode);
                }

                if (entryType.getHeadLength() == 0)
                    special = true;

                pe.setData(listRawData, special);

                RecordDecodeStatus decoded = decodeRecord(pe);

                // if (pe.getEntryType() ==
                // PumpHistoryEntryType.UnknownBasePacket)
                // {
                // decoded = RecordDecodeStatus.Unknown;
                // }
                // else
                // {
                // decoded = decodeRecord(pe);
                // }

                // System.out.println("Found entry: " + entryType.name() +
                // " ["
                // + bitUtils.getDebugByteListHex(listRawData) + "] ");

                // FIXME

                // if (!decoded)
                // System.out.println("#" + record + " " + pe);

                // if (decoded)
                // LOG.info("#" + record + " " + pe);
                // else
                // LOG.warn("#" + record + " BAD: " + pe);

                if ((decoded == RecordDecodeStatus.OK) || (decoded == RecordDecodeStatus.Ignored)) {
                    LOG.info("#" + record + " " + decoded.getDescription() + " " + pe);
                    Log.i(TAG, "#" + record + " " + decoded.getDescription() + " -- " + pe);
                } else {
                    LOG.warn("#" + record + " " + decoded.getDescription() + "  " + pe);
                    Log.w(TAG, "#" + record + " " + decoded.getDescription() + " -- " + pe);
                }

                addToStatistics(pe, decoded, null);

                record++;

                if (decoded == RecordDecodeStatus.OK) // we add only OK records, all others are ignored
                {
                    outList.add(pe);
                }
            }

            // System.out.println("Counter: " + counter);

            // if (counter > 100)
            // break;

        } while (counter < dataClear.size());

        return outList;
    }


    public RecordDecodeStatus decodeRecord(MedtronicHistoryEntry entryIn) {
        PumpHistoryEntry precord = (PumpHistoryEntry)entryIn;
        try {
            return decodeRecord(entryIn, false);
        } catch (Exception ex) {
            LOG.error("     Error decoding: type={}, ex={}", precord.getEntryType().name(), ex.getMessage(), ex);
            return RecordDecodeStatus.Error;
        }
    }


    public RecordDecodeStatus decodeRecord(MedtronicHistoryEntry entryIn, boolean x) {
        // FIXME
        // TODO
        PumpHistoryEntry entry = (PumpHistoryEntry)entryIn;

        if (entry.getDateTimeLength() > 0) {
            decodeDateTime(entry);
        }

        // LOG.debug("decodeRecord: type={}", entry.getEntryType());
        decodeDateTime(entry);

        switch (entry.getEntryType()) {
        // not implemented
            case ChangeBasalProfile_NewProfile:
            case ChangeBasalProfile_OldProfile:
            case SelectBasalProfile:
            case DailyTotals522:

                // case IanA8:
            case DailyTotals523:
            case DailyTotals512:
                return RecordDecodeStatus.NotSupported;

                // WORK IN PROGRESS

                // POSSIBLY READY

            case BasalProfileStart:
                return decodeBasalProfileStart(entry);

                // AAPS Implementation - Not yet done

                // AAPS Implementation - OK entries

                // AAPS events (Tbr, Bolus)

                // AAPS alerts

                // AAPS TDDs

                // AAPS Implementation - Ignored entries
            case CalBGForPH:
            case ChangeRemoteId:
            case ClearAlarm:
            case ChangeAlarmNotifyMode: // ChangeUtility:
            case ToggleRemote:
            case UnabsorbedInsulin:
            case BGReceived: // Ian3F: CGMS
            case SensorAlert: // Ian08 CGMS
            case ChangeTempBasalType:
            case ChangeTimeFormat:
            case ChangeReservoirWarningTime:
            case ChangeBolusReminderEnable:
            case ChangeBolusReminderTime:
            case ChangeChildBlockEnable:
            case ChangeMaxBolus:
            case ChangeMaxBasal:
            case BolusWizardEnabled:
            case ChangeBGReminderOffset:
            case ChangeAlarmClockTime:
            case ChangeMeterId:
            case ChangeParadigmLinkID:
            case JournalEntryMealMarker:
            case JournalEntryExerciseMarker:
            case DeleteBolusReminderTime:
            case SetAutoOff:
            case SelfTest:
            case ClearSettings:
            case JournalEntryInsulinMarker:
            case JournalEntryOtherMarker:
            case ChangeBolusWizardSetup:
            case ChangeSensorSetup2:
            case RestoreMystery51:
            case RestoreMystery52:
            case ChangeSensorAlarmSilenceConfig:
            case RestoreMystery54:
            case RestoreMystery55:
            case ChangeSensorRateOfChangeAlertSetup:
            case ChangeBolusScrollStepSize:
            case BolusWizardChange:
            case SaveSettings:
            case ChangeVariableBolus:
            case ChangeAudioBolus:
            case ChangeBGReminderEnable:
            case ChangeAlarmClockEnable:
            case BolusReminder:
            case DeleteAlarmClockTime:
            case ChangeCarbUnits:
            case ChangeWatchdogEnable:
            case ChangeOtherDeviceID:
            case ChangeWatchdogMarriageProfile:
            case DeleteOtherDeviceID:
            case ChangeCaptureEventEnable:
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
            case EventUnknown_MM512_0x2f:
            case EventUnknown_MM512_0x37:
            case EventUnknown_MM512_0x38:
            case EventUnknown_MM512_0x39:
            case EventUnknown_MM512_0x3b:
            case EventUnknown_MM512_0x4e:
            case EventUnknown_MM522_0x70:
            case EventUnknown_MM512_0x88:
            case EventUnknown_MM512_0x94:
            case EventUnknown_MM522_0xE8:
            case EventUnknown_0x4d:
                // LOG.debug(" -- ignored Pump Entry: " + entry.getEntryType().name());
                return RecordDecodeStatus.Ignored;

                // **** Implemented records ****

            case ChangeTime:
                changeTimeRecord = entry;
                return RecordDecodeStatus.OK;

            case NewTimeSet:
                decodeChangeTime(entry);
                return RecordDecodeStatus.OK;

            case TempBasalDuration:
                decodeTempBasal(entry);
                return RecordDecodeStatus.OK;

            case TempBasalRate:
                decodeTempBasal(entry);
                return RecordDecodeStatus.OK;

            case Bolus:
                decodeBolus(entry);
                return RecordDecodeStatus.OK;

            case EndResultTotals:
                decodeEndResultTotals(entry);
                return RecordDecodeStatus.OK;

            case BatteryActivity:
                decodeBatteryActivity(entry);
                return RecordDecodeStatus.OK;

            case LowReservoir:
                decodeLowReservoir(entry);
                return RecordDecodeStatus.OK;

            case LowBattery:
                // this.writeData(PumpBaseType.Event, PumpEventType.BatteryLow, entry.getATechDate());
                return RecordDecodeStatus.OK;

            case PumpSuspend:
                // this.writeData(PumpBaseType.Event, PumpEventType.BasalStop, entry.getATechDate());
                return RecordDecodeStatus.OK;

            case PumpResume:
                // this.writeData(PumpBaseType.Event, PumpEventType.BasalRun, entry.getATechDate());
                return RecordDecodeStatus.OK;

            case Rewind:
                // this.writeData(PumpBaseType.Event, PumpEventType.CartridgeRewind, entry.getATechDate());
                return RecordDecodeStatus.OK;

            case EventUnknown_MM522_0x05:
                break;
            case NoDeliveryAlarm:
                // this.writeData(PumpBaseType.Alarm, PumpAlarms.NoDelivery, entry.getATechDate());
                return RecordDecodeStatus.OK;

            case BolusWizardBolusEstimate:
                decodeBolusWizard(entry);
                return RecordDecodeStatus.OK;

            case Prime:
                decodePrime(entry);
                return RecordDecodeStatus.OK;

            case None:
            case UnknownBasePacket:
                return RecordDecodeStatus.Error;

                // case Andy0d:

                // case Andy58:

                // case Andy90:

            default: {
                LOG.debug("Not supported: " + entry.getEntryType());
                return RecordDecodeStatus.NotSupported;
            }

        }

        return RecordDecodeStatus.Error;

    }


    // private void decodeCalBGForPH(PumpHistoryEntry entry) {
    // int high = (entry.getDatetime()[4] & 0x80) >> 7;
    // int bg = bitUtils.toInt(high, getUnsignedInt(entry.getHead()[0]));
    //
    // writeData(PumpBaseType.AdditionalData, PumpAdditionalDataType.BloodGlucose, "" + bg, entry.getATechDate());
    // }

    // masks = [ ( 0x80, 7), (0x40, 6), (0x20, 5), (0x10, 4) ]
    // nibbles = [ ]
    // for mask, shift in masks:
    // nibbles.append( ( (year & mask) >> shift ) )
    // return nibbles

    // FIXME
    private void decodeChangeTime(PumpHistoryEntry entry) {
        if (changeTimeRecord == null)
            return;

        // String timeChange = String.format(PumpEventType.DateTimeChanged.getValueTemplate(),
        // this.changeTimeRecord.getATechDate().getDateTimeString(), entry.getATechDate().getDateTimeString());

        // writeData(PumpBaseType.Event, PumpEventType.DateTimeChanged, timeChange, entry.getATechDate());

        this.changeTimeRecord = null;
    }


    // FIXME
    private void decodeBatteryActivity(PumpHistoryEntry entry) {
        // this.writeData(PumpBaseType.Event, entry.getHead()[0] == 0 ? PumpEventType.BatteryRemoved :
        // PumpEventType.BatteryReplaced, entry.getATechDate());
    }


    // FIXME 554 ?
    private void decodeEndResultTotals(PumpHistoryEntry entry) {
        float totals = bitUtils.toInt((int)entry.getHead()[0], (int)entry.getHead()[1], (int)entry.getHead()[2],
            (int)entry.getHead()[3], ByteUtil.BitConversion.BIG_ENDIAN) * 0.025f;

        entry.addDecodedData("Totals", totals);

        // this.writeData(PumpBaseType.Report, PumpReport.InsulinTotalDay, getFormattedFloat(totals, 2),
        // entry.getATechDate());
    }


    // FIXME
    private RecordDecodeStatus decodeBasalProfileStart(PumpHistoryEntry entry) {
        byte[] body = entry.getBody();
        // int bodyOffset = headerSize + timestampSize;
        int offset = body[0] * 1000 * 30 * 60;
        Float rate = null;
        int index = body[2];

        if (MedtronicDeviceType.isSameDevice(MedtronicUtil.getMedtronicPumpModel(),
            MedtronicDeviceType.Medtronic_523andHigher)) {
            rate = body[1] * 0.025f;
        }

        if (rate == null) {
            LOG.warn("Basal Profile Start (ERROR): offset={}, rate={}, index={}, body_raw={}", offset, rate, index,
                body);
            return RecordDecodeStatus.Error;
        } else {
            // writeData(PumpBaseType.Basal, PumpBasalType.ValueChange, getFormattedFloat(rate, 3),
            // entry.getATechDate());
            entry.addDecodedData("Value", getFormattedFloat(rate, 3));
            return RecordDecodeStatus.OK;
        }

    }


    private void decodeBolusWizard(PumpHistoryEntry entry) {
        byte[] body = entry.getBody();

        BolusWizardDTO dto = new BolusWizardDTO();

        float bolus_strokes = 10.0f;

        if (MedtronicDeviceType.isSameDevice(MedtronicUtil.getMedtronicPumpModel(),
            MedtronicDeviceType.Medtronic_523andHigher)) {
            // https://github.com/ps2/minimed_rf/blob/master/lib/minimed_rf/log_entries/bolus_wizard.rb#L102
            bolus_strokes = 40.0f;

            dto.carbs = ((body[1] & 0x0c) << 6) + body[0];

            dto.bloodGlucose = ((body[1] & 0x03) << 8) + entry.getHead()[0];
            dto.carbRatio = body[1] / 10.0f;
            // carb_ratio (?) = (((self.body[2] & 0x07) << 8) + self.body[3]) /
            // 10.0s
            dto.insulinSensitivity = new Float(body[4]);
            dto.bgTargetLow = (int)body[5];
            dto.bgTargetHigh = (int)body[14];
            dto.correctionEstimate = (((body[9] & 0x38) << 5) + body[6]) / bolus_strokes;
            dto.foodEstimate = ((body[7] << 8) + body[8]) / bolus_strokes;
            dto.unabsorbedInsulin = ((body[10] << 8) + body[11]) / bolus_strokes;
            dto.bolusTotal = ((body[12] << 8) + body[13]) / bolus_strokes;
        } else {
            dto.bloodGlucose = (((body[1] & 0x0F) << 8) | entry.getHead()[0]);
            dto.carbs = (int)body[0];
            dto.carbRatio = new Float(body[2]);
            dto.insulinSensitivity = new Float(body[3]);
            dto.bgTargetLow = (int)body[4];
            dto.bgTargetHigh = (int)body[12];
            dto.bolusTotal = body[11] / 10.0f;
            dto.foodEstimate = body[6] / 10.0f;
            dto.unabsorbedInsulin = body[9] / 10.0f;
            dto.bolusTotal = body[11] / 10.0f;
            dto.correctionEstimate = (body[7] + (body[5] & 0x0F)) / 10.0f;
        }

        dto.localDateTime = entry.getLocalDateTime();
        entry.addDecodedData("Object", dto);
        // entry.setHistoryEntryDetails(dto);

        // this.writeData(PumpBaseType.Event, PumpEventType.BolusWizard, dto.getValue(), entry.getATechDate());

    }


    // FIXME
    private void decodeLowReservoir(PumpHistoryEntry entry) {
        float amount = (getUnsignedInt(entry.getHead()[0]) * 1.0f / 10.0f);
        // this.writeData(PumpBaseType.Event, PumpEventType.ReservoirLowDesc, getFormattedFloat(amount, 1),
        // entry.getATechDate());
    }


    // FIXME
    private void decodePrime(PumpHistoryEntry entry) {
        float amount = bitUtils.toInt(entry.getHead()[2], entry.getHead()[3]) / 10.0f;
        float fixed = bitUtils.toInt(entry.getHead()[0], entry.getHead()[1]) / 10.0f;

        entry.addDecodedData("Amount", amount);
        entry.addDecodedData("FixedAmount", fixed);

        // amount = (double) (asUINT8(data[4]) << 2) / 40.0;
        // programmedAmount = (double) (asUINT8(data[2]) << 2) / 40.0;
        // primeType = programmedAmount == 0 ? "manual" : "fixed";
        // return true;

        // this.writeData(PumpBaseType.Event, PumpEventType.PrimeInfusionSet, fixed > 0 ? getFormattedFloat(fixed, 1) :
        // getFormattedFloat(amount, 1), entry.getATechDate());
    }


    @Override
    public void postProcess() {
        // if (bolusEntry != null) {
        // writeBolus(pumpHistoryEntry4BolusEntry, bolusEntry);
        // }
    }


    @Override
    protected void runPostDecodeTasks() {
        this.showStatistics();
    }


    private void decodeBolus(PumpHistoryEntry entry) {
        BolusDTO bolus = new BolusDTO();

        byte[] data = entry.getHead();

        if (MedtronicDeviceType.isSameDevice(MedtronicUtil.getMedtronicPumpModel(),
            MedtronicDeviceType.Medtronic_523andHigher)) {
            bolus.setRequestedAmount(bitUtils.toInt(data[0], data[1]) / 40.0f);
            bolus.setDeliveredAmount(bitUtils.toInt(data[2], data[3]) / 10.0f);
            bolus.setInsulinOnBoard(bitUtils.toInt(data[4], data[5]) / 40.0f);
            bolus.setDuration(data[6] * 30);
        } else {
            bolus.setRequestedAmount(data[0] / 40.0f);
            bolus.setDeliveredAmount(data[1] / 10.0f);
            bolus.setDuration(data[2] * 30);
        }

        bolus.setBolusType((bolus.getDuration() != null && (bolus.getDuration() > 0)) ? PumpBolusType.Extended
            : PumpBolusType.Normal);
        bolus.setLocalDateTime(entry.getLocalDateTime());

        String dateTime = StringUtil.toDateTimeString(entry.getLocalDateTime());

        if (bolus.getBolusType() == PumpBolusType.Extended) {
            // we check if we have coresponding normal entry
            if (bolusHistory.containsKey(dateTime)) {
                BolusDTO bolusDTO = bolusHistory.get(dateTime);

                bolusDTO.setImmediateAmount(bolus.getDeliveredAmount());
                bolusDTO.setBolusType(PumpBolusType.Multiwave);

                return;
            }
        }

        entry.addDecodedData("Object", bolus);

        bolusHistory.put(dateTime, bolus);

    }


    // FIXME new pumps have single record (I think)
    private void decodeTempBasal(PumpHistoryEntry entry) {

        if (this.tbrPreviousRecord == null) {
            // LOG.debug(this.tbrPreviousRecord.toString());
            this.tbrPreviousRecord = entry;
            return;
        }

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

        // LOG.debug("Rate: " + tbrRate.toString());
        // LOG.debug("Durration: " + tbrDuration.toString());

        // if ((asUINT8(data[7]) >> 3) == 0) {
        // mIsPercent = false;
        // tbrRate = (double) (asUINT8(tbrRate.getRawData().get(1)) / 40.0;
        // } else {
        // mIsPercent = true;
        // basalRate = asUINT8(data[1]);
        // }

        // FIXME
        TempBasalPair tbr = new TempBasalPair(tbrRate.getHead()[0], tbrDuration.getHead()[0], (ByteUtil.asUINT8(tbrRate
            .getDatetime()[4]) >> 3) == 0);

        // System.out.println("TBR: amount=" + tbr.getInsulinRate() + ", duration=" + tbr.getDurationMinutes()
        // // + " min. Packed: " + tbr.getValue()
        // );

        entry.addDecodedData("Object", tbr);

        tbrPreviousRecord = null;
    }


    private void decodeDateTime(PumpHistoryEntry entry) {
        byte[] dt = entry.getDatetime();

        if (dt == null) {
            LOG.warn("DateTime not set.");
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

            LocalDateTime atdate = new LocalDateTime(year, month, dayOfMonth, hour, minutes, seconds);

            entry.setLocalDateTime(atdate);
        } else if (entry.getDateTimeLength() == 2) {
            int low = ByteUtil.asUINT8(dt[0]) & 0x1F;
            int mhigh = (ByteUtil.asUINT8(dt[0]) & 0xE0) >> 4;
            int mlow = (ByteUtil.asUINT8(dt[1]) & 0x80) >> 7;
            int month = mhigh + mlow;
            int dayOfMonth = low + 1;
            int year = 2000 + (ByteUtil.asUINT8(dt[1]) & 0x7F);

            // LocalDate rval = new LocalDate(year, month, dayOfMonth);

            // int dayOfMonth = dt[0] & 0x1F;
            // int month = (((dt[0] & 0xE0) >> 4) + ((dt[1] & 0x80) >> 7));
            // int year = fix2DigitYear(dt[1] & 0x3F);

            LocalDateTime atdate = new LocalDateTime(year, month, dayOfMonth, 0, 0);

            entry.setLocalDateTime(atdate);
        } else {
            LOG.warn("Unknown datetime format: " + entry.getDateTimeLength());
        }
        // return new DateTime(year + 2000, month, dayOfMonth, hour, minutes,
        // seconds);

    }


    private int fix2DigitYear(int year) {
        if (year > 90) {
            year += 1900;
        } else {
            year += 2000;
        }

        return year;
    }

    // WRITE DATA

    // private void writeData(PumpBaseType baseType, CodeEnumWithTranslation subType, ATechDate aTechDate) {
    // this.pumpValuesWriter.writeObject(baseType.name() + "_" + subType.getName(), aTechDate);
    // }
    //
    //
    // private void writeData(PumpBaseType baseType, CodeEnumWithTranslation subType, String value, ATechDate aTechDate)
    // {
    // this.pumpValuesWriter.writeObject(baseType.name() + "_" + subType.getName(), aTechDate, value);
    // }

}
