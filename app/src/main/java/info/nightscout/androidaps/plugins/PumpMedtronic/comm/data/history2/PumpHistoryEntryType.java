package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history2;


import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Application:   GGC - GNU Gluco Control
 * Plug-in:       GGC PlugIn Base (base class for all plugins)
 * <p>
 * See AUTHORS for copyright information.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 * <p>
 * Filename:     PumpHistoryEntryType
 * Description:  Pump History Entry Type.
 * <p>
 * Data is from several sources, so in comments there are "versions".
 * Version:
 * v1 - default doc from decoding-carelink
 * v2 - nightscout code
 * v3 - testing
 * v4 - Andy testing (?)
 * v5 - Loop code and another batch of testing with 512
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum PumpHistoryEntryType //implements CodeEnum
{

    None(0, "None", 1, 0, 0), // Bolus(0x01, "Bolus", 4, 5, 4), // 4,5,0 -> 4,5,4 Bolus(0x01, "Bolus", 2, 5, 4),

    Bolus(0x01, "Bolus", 4, DateFormat.LongDate, 0), // 523+[H=8]

    Prime(0x03, "Prime", 5, 5, 0), //

    EventUnknown_MM522_0x05((byte) 0x05, 2, 5, 28), //

    NoDeliveryAlarm(0x06, "NoDelivery", 4, 5, 0), //
    EndResultTotals(0x07, "ResultTotals", 5, 2, 0), // V1: 5/5/41 V2: 5,2,3 V3, 5,2,0  V5: 7/10(523)
    ChangeBasalProfile_OldProfile(0x08, 2, 5, 145), // // V1: 2,5,42 V2:2,5,145; V4: V5
    ChangeBasalProfile_NewProfile(0x09, 2, 5, 145), //
    EventUnknown_MM512_0x10(0x10), // 29, 5, 0
    CalBGForPH(0x0a, "CalBGForPH"), //
    SensorAlert(0x0b, "SensorAlert", 3, 5, 0), // Ian08
    ClearAlarm(0x0c, "ClearAlarm", 2, 5, 0), // 2,5,4

    //Andy0d(0x0d, "Unknown", 2, 5, 0),

    SelectBasalProfile(0x14, "SelectBasalProfile"), //
    TempBasalDuration(0x16, "TempBasalDuration"), //
    ChangeTime(0x17, "ChangeTime"), //
    NewTimeSet(0x18, "NewTimeSet"), //
    LowBattery(0x19, "LowBattery"), //
    BatteryActivity(0x1a, "Battery Activity"), //
    SetAutoOff(0x1b, "SetAutoOff"), //
    PumpSuspend(0x1e, "PumpSuspend"), //
    PumpResume(0x1f, "PumpResume"), //
    SelfTest(0x20, "SelfTest"), //
    Rewind(0x21, "Rewind"), //
    ClearSettings(0x22, "ClearSettings"), // 8?
    ChangeChildBlockEnable(0x23, "ChangeChildBlockEnable"),  // 8?
    ChangeMaxBolus(0x24), // 8?
    EventUnknown_MM522_0x25(0x25), // 8?
    ToggleRemote(0x26, "EnableDisableRemote", 2, 5, 0), //  2, 5, 14
    ChangeRemoteId(0x27, "ChangeRemoteID"), // ??

    ChangeMaxBasal(0x2c), //
    BolusWizardEnabled(0x2d), // V3 ?
    EventUnknown_MM512_0x2e(0x2e), //
    EventUnknown_MM512_0x2f(0x2f), //
    ChangeBGReminderOffset(0x31), //
    ChangeAlarmClockTime(0x32), //
    TempBasalRate(0x33, "TempBasal", 2, 5, 1), //
    LowReservoir(0x34), //

    ChangeMeterId(0x36), //
    EventUnknown_MM512_0x37(0x37), // V:MM512
    EventUnknown_MM512_0x38(0x38), //
    EventUnknown_MM512_0x39(0x39), //
    EventUnknown_MM512_0x3b(0x3b), //
    ChangeParadigmLinkID(0x3c), // V3 ?

    BGReceived(0x3f, "BGReceived", 2, 5, 3), // Ian3F
    JournalEntryMealMarker(0x40, 2, 5, 2),  //
    JournalEntryExerciseMarker(0x41, 2, 5, 1),  // ?? JournalEntryExerciseMarkerPumpEvent
    JournalEntryInsulinMarker(0x42, 2, 5, 1),  // ?? InsulinMarkerEvent
    JournalEntryOtherMarker(0x43),  //
    EventUnknown_MM522_0x45(0x45, 2, 5, 1), //
    EventUnknown_MM522_0x46(0x46, 2, 5, 1), //
    EventUnknown_MM522_0x47(0x47, 2, 5, 1), //
    EventUnknown_MM522_0x48(0x48, 2, 5, 1), //
    EventUnknown_MM522_0x49(0x49, 2, 5, 1), //
    EventUnknown_MM522_0x4a(0x4a, 2, 5, 1), //
    EventUnknown_MM522_0x4b(0x4b, 2, 5, 1), //
    EventUnknown_MM522_0x4c(0x4c, 2, 5, 1), //


    EventUnknown_0x4d(0x4d), // V5: 512: 7, 522: 8 ????NS
    EventUnknown_MM512_0x4e(0x4e), //

    ChangeBolusWizardSetup(0x4f, 2, 5, 32), //
    ChangeSensorSetup2(0x50, 2, 5, 30),  // Ian50
    RestoreMystery51(0x51), //
    RestoreMystery52(0x52), //
    ChangeSensorAlarmSilenceConfig(0x53, 2, 5, 1), // 8
    RestoreMystery54(0x54), // Ian54
    RestoreMystery55(0x55), //
    ChangeSensorRateOfChangeAlertSetup(0x56, 2, 5, 5),  // 12
    ChangeBolusScrollStepSize(0x57),  //


    // V4
    //Andy58(0x58, "Unknown", 13, 5, 0), // TODO is this one really there ???


    BolusWizardChange(0x5a, "BolusWizard", 2, 5, 117), // V2: 522+[B=143]
    BolusWizardBolusEstimate(0x5b, "BolusWizardBolusEstimate", 2, 5, 13), // 15 // V2: 523+[B=15]
    UnabsorbedInsulin(0x5c, "UnabsorbedInsulinBolus", 5, 0, 0), // head[1] -> body length
    SaveSettings(0x5d), //
    ChangeVariableBolus(0x5e),  //
    ChangeAudioBolus(0x5f, "EasyBolusEnabled"), // V3 ?
    ChangeBGReminderEnable(0x60), // questionable60
    ChangeAlarmClockEnable((byte) 0x61),  //
    ChangeTempBasalType((byte) 0x62),  // ChangeTempBasalTypePumpEvent
    ChangeAlarmNotifyMode(0x63),  //
    ChangeTimeFormat(0x64),  //
    ChangeReservoirWarningTime((byte) 0x65),  //
    ChangeBolusReminderEnable(0x66, 2, 5, 2),  // 9
    ChangeBolusReminderTime((byte) 0x67, 2, 5, 2),  // 9
    DeleteBolusReminderTime((byte) 0x68, 2, 5, 2),  // 9
    BolusReminder(0x69, 2, 5, 0), // Ian69
    DeleteAlarmClockTime(0x6a, "DeleteAlarmClockTime", 2, 5, 7),  // 14

    DailyTotals512(0x6c, "Daily Totals 512", 0, 0, 36), //
    DailyTotals522(0x6d, "Daily Totals 522", 1, 2, 41), // // hack1(0x6d, "hack1", 46, 5, 0),
    DailyTotals523(0x6e, "Daily Totals 523", 1, 2, 49), // 1102014-03-17T00:00:00
    ChangeCarbUnits((byte) 0x6f),  //

    EventUnknown_MM522_0x70((byte) 0x70, 2, 5, 1), //


    BasalProfileStart(0x7b, 2, 5, 3), // // 722
    ChangeWatchdogEnable((byte) 0x7c), //
    ChangeOtherDeviceID((byte) 0x7d, 2, 5, 30),  //

    ChangeWatchdogMarriageProfile((byte) 0x81, 2, 5, 5),  // 12
    DeleteOtherDeviceID((byte) 0x82, 2, 5, 5),  //
    ChangeCaptureEventEnable((byte) 0x83), //

    EventUnknown_MM512_0x88((byte) 0x88), //
    EventUnknown_MM512_0x94((byte) 0x94), //
    //IanA8(0xA8, "xx", 10, 5, 0), //

    //Andy90(0x90, "Unknown", 7, 5, 0),

    //AndyB4(0xb4, "Unknown", 7, 5, 0),
    // Andy4A(0x4a, "Unknown", 5, 5, 0),

    // head[1],
    // body[49] op[0x6e]

    EventUnknown_MM522_0xE8(0xe8, 2, 5, 25), //

    UnknownBasePacket(0xFF, "Unknown Base Packet");

    // private PumpHistoryEntryType(String description, List<Integer> opCode,
    // byte length)
    // {
    //
    // }

    private int opCode;
    private String description;
    private int headLength = 0;
    private int dateLength;
    private int bodyLength;
    private int totalLength;
    // private MinimedDeviceType deviceType;

    // special rules need to be put in list from highest to lowest (e.g.:
    // 523andHigher=12, 515andHigher=10 and default (set in cnstr) would be 8)
    private List<SpecialRule> specialRulesHead;
    private List<SpecialRule> specialRulesBody;
    private boolean hasSpecialRules = false;

    private static Map<Integer, PumpHistoryEntryType> opCodeMap = new HashMap<Integer, PumpHistoryEntryType>();

    static {
        for (PumpHistoryEntryType type : values()) {
            opCodeMap.put(type.opCode, type);
        }

        setSpecialRulesForEntryTypes();
    }


    static void setSpecialRulesForEntryTypes() {
        EndResultTotals.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 3));
        Bolus.addSpecialRuleHead(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 8));
        BolusWizardChange.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_522andHigher, 143));
        BolusWizardBolusEstimate.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 15));
        BolusReminder.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 2));
    }


    PumpHistoryEntryType(int opCode, String name) {
        this(opCode, name, 2, 5, 0);
    }


    PumpHistoryEntryType(int opCode) {
        this(opCode, null, 2, 5, 0);
    }


    PumpHistoryEntryType(int opCode, int head, int date, int body) {
        this(opCode, null, head, date, body);
    }


    PumpHistoryEntryType(int opCode, String name, int head, DateFormat dateFormat, int body) {
        this(opCode, name, head, dateFormat.getLength(), body);
    }


    PumpHistoryEntryType(int opCode, String name, int head, int date, int body) {
        this.opCode = (byte) opCode;
        this.description = name;
        this.headLength = head;
        this.dateLength = date;
        this.bodyLength = body;
        this.totalLength = (head + date + body);
    }


    public int getCode() {
        return this.opCode;
    }


    //
    // private PumpHistoryEntryType(int opCode, String name, int head, int date,
    // int body)
    // {
    // this.opCode = (byte) opCode;
    // this.description = name;
    // this.headLength = head;
    // this.dateLength = date;
    // this.bodyLength = body;
    // this.totalLength = (head + date + body);
    // }
    //


    public int getTotalLength() {
        if (hasSpecialRules()) {
            return getHeadLength() + getBodyLength() + getDateLength();
        } else {
            return totalLength;
        }
    }


    private boolean hasSpecialRules() {
        return hasSpecialRules;
    }


    void addSpecialRuleHead(SpecialRule rule) {
        if (CollectionUtils.isEmpty(specialRulesHead)) {
            specialRulesHead = new ArrayList<SpecialRule>();
        }

        specialRulesHead.add(rule);
        hasSpecialRules = true;
    }


    void addSpecialRuleBody(SpecialRule rule) {
        if (CollectionUtils.isEmpty(specialRulesBody)) {
            specialRulesBody = new ArrayList<SpecialRule>();
        }

        specialRulesBody.add(rule);
        hasSpecialRules = true;
    }


    public static PumpHistoryEntryType getByCode(int opCode) {
        if (opCodeMap.containsKey(opCode)) {
            return opCodeMap.get(opCode);
        } else {
            return PumpHistoryEntryType.UnknownBasePacket;
        }
    }


    public int getOpCode() {
        return opCode;
    }


    public String getDescription() {
        return this.description == null ? name() : this.description;
    }


    public int getHeadLength() {
        if (hasSpecialRules) {
            if (CollectionUtils.isNotEmpty(specialRulesHead)) {
                return determineSizeByRule(headLength, specialRulesHead);
            } else {
                return headLength;
            }
        } else {
            return headLength;
        }
    }


    public int getDateLength() {
        return dateLength;
    }


    public int getBodyLength() {
        if (hasSpecialRules) {
            if (CollectionUtils.isNotEmpty(specialRulesBody)) {
                return determineSizeByRule(bodyLength, specialRulesBody);
            } else {
                return bodyLength;
            }
        } else {
            return bodyLength;
        }
    }


    private int determineSizeByRule(int defaultValue, List<SpecialRule> rules) {
        int size = defaultValue;

        for (SpecialRule rule : rules) {
            if (MedtronicDeviceType.isSameDevice(MedtronicUtil.getDeviceType(), rule.deviceType)) {
                size = rule.size;
                break;
            }
        }

        return size;
    }

    // byte[] dh = { 2, 3 };

    public static class SpecialRule {

        MedtronicDeviceType deviceType;
        int size;


        public SpecialRule(MedtronicDeviceType deviceType, int size) {
            this.deviceType = deviceType;
            this.size = size;
        }
    }


    enum DateFormat {
        None(0), //
        LongDate(5), //
        ShortDate(2);

        private int length;


        DateFormat(int length) {
            this.length = length;
        }


        public int getLength() {
            return length;
        }


        public void setLength(int length) {
            this.length = length;
        }
    }

}
