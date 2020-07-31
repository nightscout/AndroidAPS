package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms;

import java.util.HashMap;
import java.util.Map;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */

public enum CGMSHistoryEntryType {

    None(0, "None", 1, 0, 0, DateType.None), //

    DataEnd(0x01, "DataEnd", 1, 0, 0, DateType.PreviousTimeStamp), //
    SensorWeakSignal(0x02, "SensorWeakSignal", 1, 0, 0, DateType.PreviousTimeStamp), //
    SensorCal(0x03, "SensorCal", 1, 0, 1, DateType.PreviousTimeStamp), //
    SensorPacket(0x04, "SensorPacket", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorError(0x05, "SensorError", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorDataLow(0x06, "SensorDataLow", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorDataHigh(0x07, "SensorDataHigh", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorTimestamp(0x08, "SensorTimestamp", 1, 4, 0, DateType.MinuteSpecific), //
    BatteryChange(0x0a, "BatteryChange", 1, 4, 0, DateType.MinuteSpecific), //
    SensorStatus(0x0b, "SensorStatus", 1, 4, 0, DateType.MinuteSpecific), //
    DateTimeChange(0x0c, "DateTimeChange", 1, 4, 0, DateType.SecondSpecific), //
    SensorSync(0x0d, "SensorSync',packet_size=4", 1, 4, 0, DateType.MinuteSpecific), //
    CalBGForGH(0x0e, "CalBGForGH',packet_size=5", 1, 4, 1, DateType.MinuteSpecific), //
    SensorCalFactor(0x0f, "SensorCalFactor", 1, 4, 2, DateType.MinuteSpecific), //
    Something10(0x10, "10-Something", 1, 4, 0, DateType.MinuteSpecific), //
    Something19(0x13, "19-Something", 1, 0, 0, DateType.PreviousTimeStamp),
    GlucoseSensorData(0xFF, "GlucoseSensorData", 1, 0, 0, DateType.PreviousTimeStamp);

    private static Map<Integer, CGMSHistoryEntryType> opCodeMap = new HashMap<>();

    static {
        for (CGMSHistoryEntryType type : values()) {
            opCodeMap.put(type.opCode, type);
        }
    }

    public boolean schemaSet;
    private int opCode;
    private String description;
    private int headLength;
    private int dateLength;
    private int bodyLength;
    private int totalLength;
    private DateType dateType;


    CGMSHistoryEntryType(int opCode, String name, int head, int date, int body, DateType dateType) {
        this.opCode = opCode;
        this.description = name;
        this.headLength = head;
        this.dateLength = date;
        this.bodyLength = body;
        this.totalLength = (head + date + body);
        this.schemaSet = true;
        this.dateType = dateType;
    }


    // private CGMSHistoryEntryType(int opCode, String name, int length)
    // {
    // this.opCode = opCode;
    // this.description = name;
    // this.headLength = 0;
    // this.dateLength = 0;
    // this.bodyLength = 0;
    // this.totalLength = length + 1; // opCode
    // }

    public static CGMSHistoryEntryType getByCode(int opCode) {
        if (opCodeMap.containsKey(opCode)) {
            return opCodeMap.get(opCode);
        } else
            return CGMSHistoryEntryType.None;
    }


    public int getCode() {
        return this.opCode;
    }


    public int getTotalLength() {
        return totalLength;
    }


    public int getOpCode() {
        return opCode;
    }


    public String getDescription() {
        return description;
    }


    public int getHeadLength() {
        return headLength;
    }


    public int getDateLength() {
        return dateLength;
    }


    public int getBodyLength() {
        return bodyLength;
    }


    public DateType getDateType() {
        return dateType;
    }


    public boolean hasDate() {
        return (this.dateType == DateType.MinuteSpecific) || (this.dateType == DateType.SecondSpecific);
    }

    public enum DateType {
        None, //
        MinuteSpecific, //
        SecondSpecific, //
        PreviousTimeStamp //

    }

}
