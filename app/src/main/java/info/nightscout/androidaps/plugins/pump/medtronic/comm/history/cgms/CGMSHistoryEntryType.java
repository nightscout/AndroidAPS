package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms;

import java.util.HashMap;
import java.util.Map;

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
 * Filename: PumpHistoryEntryType Description: Pump History Entry Type.
 * <p>
 * Data is from several sources, so in comments there are "versions". Version: v1 - default doc fromc decoding-carelink
 * v2 - Andy testing (?)
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum CGMSHistoryEntryType {

    None(0, "None", 1, 0, 0, DateType.None), //

    DataEnd(0x01, "DataEnd", 1, 0, 0, DateType.None), //

    SensorWeakSignal(0x02, "SensorWeakSignal", 1, 0, 0, DateType.PreviousTimeStamp), //

    SensorCal(0x03, "SensorCal", 1, 0, 1, DateType.PreviousTimeStamp), //

    SensorTimestamp(0x08, "SensorTimestamp", 1, 4, 0, DateType.MinuteSpecific), //

    BatteryChange(0x0a, "BatteryChange',packet_size=4", 1, 4, 0, DateType.MinuteSpecific), //

    SensorStatus(0x0b, "SensorStatus", 1, 4, 0, DateType.MinuteSpecific), //

    DateTimeChange(0x0c, "DateTimeChange", 1, 4, 0, DateType.SecondSpecific), //

    SensorSync(0x0d, "SensorSync',packet_size=4", 1, 4, 0, DateType.MinuteSpecific), //

    CalBGForGH(0x0e, "CalBGForGH',packet_size=5", 1, 4, 1, DateType.MinuteSpecific), //

    SensorCalFactor(0x0f, "SensorCalFactor", 1, 4, 2, DateType.MinuteSpecific), //

    // # 0x10: '10-Something',packet_size=7,date_type='minSpecific',op='0x10'),

    Something10(0x10, "10-Something", 1, 4, 0, DateType.MinuteSpecific), //

    Something19(0x13, "19-Something", 1, 0, 0, DateType.PreviousTimeStamp),

    // V2
    Something05(0x05, "05-Something", 1, 0, 0, DateType.PreviousTimeStamp),

    GlucoseSensorData(0xFF, "GlucoseSensorData", 1, 0, 0, DateType.PreviousTimeStamp);
    ;

    private static Map<Integer, CGMSHistoryEntryType> opCodeMap = new HashMap<Integer, CGMSHistoryEntryType>();

    static {
        for (CGMSHistoryEntryType type : values()) {
            opCodeMap.put(type.opCode, type);
        }
    }

    public boolean schemaSet = false;
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
        ;

    }

}
