package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RecordDecodeStatus;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */

public class MedtronicCGMSHistoryDecoder extends MedtronicHistoryDecoder<CGMSHistoryEntry> {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);


    // CGMSValuesWriter cgmsValuesWriter = null;

    public MedtronicCGMSHistoryDecoder() {
    }


    public RecordDecodeStatus decodeRecord(CGMSHistoryEntry record) {
        try {
            return decodeRecord(record, false);
        } catch (Exception ex) {
            LOG.error("     Error decoding: type={}, ex={}", record.getEntryType().name(), ex.getMessage(), ex);
            return RecordDecodeStatus.Error;
        }
    }


    public RecordDecodeStatus decodeRecord(CGMSHistoryEntry entry, boolean x) {

        if (entry.getDateTimeLength() > 0) {
            parseDate(entry);
        }

        switch (entry.getEntryType()) {

            case SensorPacket:
                decodeSensorPacket(entry);
                break;

            case SensorError:
                decodeSensorError(entry);
                break;

            case SensorDataLow:
                decodeDataHighLow(entry, 40);
                break;

            case SensorDataHigh:
                decodeDataHighLow(entry, 400);
                break;

            case SensorTimestamp:
                decodeSensorTimestamp(entry);
                break;

            case SensorCal:
                decodeSensorCal(entry);
                break;

            case SensorCalFactor:
                decodeSensorCalFactor(entry);
                break;

            case SensorSync:
                decodeSensorSync(entry);
                break;

            case SensorStatus:
                decodeSensorStatus(entry);
                break;

            case CalBGForGH:
                decodeCalBGForGH(entry);
                break;

            case GlucoseSensorData:
                decodeGlucoseSensorData(entry);
                break;

            // just timestamp
            case BatteryChange:
            case Something10:
            case DateTimeChange:
                break;

            // just relative timestamp
            case Something19:
            case DataEnd:
            case SensorWeakSignal:
                break;

            case None:
                break;

        }

        return RecordDecodeStatus.NotSupported;
    }


    @Override
    public void postProcess() {

    }


    public List<CGMSHistoryEntry> createRecords(List<Byte> dataClearInput) {

        List<Byte> dataClear = reverseList(dataClearInput, Byte.class);

        prepareStatistics();

        int counter = 0;

        List<CGMSHistoryEntry> outList = new ArrayList<CGMSHistoryEntry>();

        // create CGMS entries (without dates)
        do {
            int opCode = getUnsignedInt(dataClear.get(counter));
            counter++;

            CGMSHistoryEntryType entryType;

            if (opCode == 0) {
                // continue;
            } else if ((opCode > 0) && (opCode < 20)) {
                entryType = CGMSHistoryEntryType.getByCode(opCode);

                if (entryType == CGMSHistoryEntryType.None) {
                    this.unknownOpCodes.put(opCode, opCode);
                    LOG.warn("GlucoseHistoryEntry with unknown code: " + opCode);

                    CGMSHistoryEntry pe = new CGMSHistoryEntry();
                    pe.setEntryType(CGMSHistoryEntryType.None);
                    pe.setOpCode(opCode);

                    pe.setData(Arrays.asList((byte) opCode), false);

                    outList.add(pe);
                } else {
                    // System.out.println("OpCode: " + opCode);

                    List<Byte> listRawData = new ArrayList<Byte>();
                    listRawData.add((byte) opCode);

                    for (int j = 0; j < (entryType.getTotalLength() - 1); j++) {
                        listRawData.add(dataClear.get(counter));
                        counter++;
                    }

                    CGMSHistoryEntry pe = new CGMSHistoryEntry();
                    pe.setEntryType(entryType);

                    pe.setOpCode(opCode);
                    pe.setData(listRawData, false);

                    // System.out.println("Record: " + pe);

                    outList.add(pe);
                }
            } else {
                CGMSHistoryEntry pe = new CGMSHistoryEntry();
                pe.setEntryType(CGMSHistoryEntryType.GlucoseSensorData);

                pe.setData(Arrays.asList((byte) opCode), false);

                outList.add(pe);
            }

        } while (counter < dataClear.size());

        List<CGMSHistoryEntry> reversedOutList = reverseList(outList, CGMSHistoryEntry.class);

        Long timeStamp = null;
        LocalDateTime dateTime = null;
        int getIndex = 0;

        for (CGMSHistoryEntry entry : reversedOutList) {

            decodeRecord(entry);

            if (entry.hasTimeStamp()) {
                timeStamp = entry.atechDateTime;
                dateTime = DateTimeUtil.toLocalDateTime(timeStamp);
                getIndex = 0;
            } else if (entry.getEntryType() == CGMSHistoryEntryType.GlucoseSensorData) {
                getIndex++;
                if (dateTime != null)
                    entry.setDateTime(dateTime, getIndex);
            } else {
                if (dateTime != null)
                    entry.setDateTime(dateTime, getIndex);
            }

            if (isLogEnabled())
                LOG.debug("Record: {}", entry);
        }

        return reversedOutList;

    }


    private <E> List<E> reverseList(List<E> dataClearInput, Class<E> clazz) {

        List<E> outList = new ArrayList<E>();

        for (int i = dataClearInput.size() - 1; i > 0; i--) {
            outList.add(dataClearInput.get(i));
        }

        return outList;
    }


    private int parseMinutes(int one) {
        return (one & Integer.parseInt("0111111", 2));
    }


    private int parseHours(int one) {
        return (one & 0x1F);
    }


    private int parseDay(int one) {
        return one & 0x1F;
    }


    private int parseMonths(int first_byte, int second_byte) {

        int first_two_bits = first_byte >> 6;
        int second_two_bits = second_byte >> 6;

        return (first_two_bits << 2) + second_two_bits;
    }


    private int parseYear(int year) {
        return (year & 0x0F) + 2000;
    }


    private Long parseDate(CGMSHistoryEntry entry) {

        if (!entry.getEntryType().hasDate())
            return null;

        byte[] data = entry.getDatetime();

        if (entry.getEntryType().getDateType() == CGMSHistoryEntryType.DateType.MinuteSpecific) {

            Long atechDateTime = DateTimeUtil.toATechDate(parseYear(data[3]), parseMonths(data[0], data[1]),
                    parseDay(data[2]), parseHours(data[0]), parseMinutes(data[1]), 0);

            entry.setAtechDateTime(atechDateTime);

            return atechDateTime;

        } else if (entry.getEntryType().getDateType() == CGMSHistoryEntryType.DateType.SecondSpecific) {
            LOG.warn("parseDate for SecondSpecific type is not implemented.");
            throw new RuntimeException();
            // return null;
        } else
            return null;

    }


    private void decodeGlucoseSensorData(CGMSHistoryEntry entry) {
        int sgv = entry.getUnsignedRawDataByIndex(0) * 2;
        entry.addDecodedData("sgv", sgv);
    }


    private void decodeCalBGForGH(CGMSHistoryEntry entry) {

        int amount = ((entry.getRawDataByIndex(3) & 0b00100000) << 3) | entry.getRawDataByIndex(5);
        //
        String originType;

        switch (entry.getRawDataByIndex(3) >> 5 & 0b00000011) {
            case 0x00:
                originType = "rf";
                break;

            default:
                originType = "unknown";

        }

        entry.addDecodedData("amount", amount);
        entry.addDecodedData("originType", originType);

    }


    private void decodeSensorSync(CGMSHistoryEntry entry) {

        String syncType;

        switch (entry.getRawDataByIndex(3) >> 5 & 0b00000011) {
            case 0x01:
                syncType = "new";
                break;

            case 0x02:
                syncType = "old";
                break;

            default:
                syncType = "find";
                break;

        }

        entry.addDecodedData("syncType", syncType);
    }


    private void decodeSensorStatus(CGMSHistoryEntry entry) {

        String statusType;

        switch (entry.getRawDataByIndex(3) >> 5 & 0b00000011) {
            case 0x00:
                statusType = "off";
                break;

            case 0x01:
                statusType = "on";
                break;

            case 0x02:
                statusType = "lost";
                break;

            default:
                statusType = "unknown";
        }

        entry.addDecodedData("statusType", statusType);

    }


    private void decodeSensorCalFactor(CGMSHistoryEntry entry) {

        double factor = (entry.getRawDataByIndex(5) << 8 | entry.getRawDataByIndex(6)) / 1000.0d;

        entry.addDecodedData("factor", factor);
    }


    private void decodeSensorCal(CGMSHistoryEntry entry) {

        String calibrationType;

        switch (entry.getRawDataByIndex(1)) {
            case 0x00:
                calibrationType = "meter_bg_now";
                break;

            case 0x01:
                calibrationType = "waiting";
                break;

            case 0x02:
                calibrationType = "cal_error";
                break;

            default:
                calibrationType = "unknown";
        }

        entry.addDecodedData("calibrationType", calibrationType);

    }


    private void decodeSensorTimestamp(CGMSHistoryEntry entry) {

        String sensorTimestampType;

        switch (entry.getRawDataByIndex(3) >> 5 & 0b00000011) {

            case 0x00:
                sensorTimestampType = "LastRf";
                break;

            case 0x01:
                sensorTimestampType = "PageEnd";
                break;

            case 0x02:
                sensorTimestampType = "Gap";
                break;

            default:
                sensorTimestampType = "Unknown";
                break;

        }

        entry.addDecodedData("sensorTimestampType", sensorTimestampType);
    }


    private void decodeSensorPacket(CGMSHistoryEntry entry) {

        String packetType;

        switch (entry.getRawDataByIndex(1)) {
            case 0x02:
                packetType = "init";
                break;

            default:
                packetType = "unknown";
        }

        entry.addDecodedData("packetType", packetType);
    }


    private void decodeSensorError(CGMSHistoryEntry entry) {

        String errorType;

        switch (entry.getRawDataByIndex(1)) {
            case 0x01:
                errorType = "end";
                break;

            default:
                errorType = "unknown";
        }

        entry.addDecodedData("errorType", errorType);
    }


    private void decodeDataHighLow(CGMSHistoryEntry entry, int sgv) {
        entry.addDecodedData("sgv", sgv);
    }


    @Override
    protected void runPostDecodeTasks() {
        this.showStatistics();
    }

}
