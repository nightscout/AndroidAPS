package info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicDeviceType;
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
 * Filename: PumpHistoryEntryType Description: Pump History Entry Type.
 * <p>
 * Data is from several sources, so in comments there are "versions". Version: v1 - default doc from decoding-carelink
 * v2 - nightscout code v3 - testing v4 - Andy testing (?) v5 - Loop code and another batch of testing with 512
 * <p>
 * Author: Andy {andy@atech-software.com}
 */

public enum PumpHistoryEntryType // implements CodeEnum
{

    None(0, "None", PumpHistoryEntryGroup.Unknown, 1, 0, 0), // Bolus(0x01, "Bolus", 4, 5, 4), // 4,5,0 -> 4,5,4
                                                             // Bolus(0x01, "Bolus", 2, 5, 4),

    Bolus(0x01, "Bolus", PumpHistoryEntryGroup.Bolus, 4, 5, 0), // 523+[H=8]

    Prime(0x03, "Prime", PumpHistoryEntryGroup.Prime, 5, 5, 0), //

    /**/EventUnknown_MM522_0x05((byte)0x05, PumpHistoryEntryGroup.Unknown, 2, 5, 28), //
    NoDeliveryAlarm(0x06, "NoDelivery", PumpHistoryEntryGroup.Alarm, 4, 5, 0), //
    EndResultTotals(0x07, "ResultTotals", PumpHistoryEntryGroup.Statistic, 5, 2, 0), // V1: 5/5/41 V2: 5,2,3 V3, 5,2,0
                                                                                     // V5: 7/10(523)
    ChangeBasalProfile_OldProfile(0x08, PumpHistoryEntryGroup.Basal, 2, 5, 145), // // V1: 2,5,42 V2:2,5,145; V4: V5
    ChangeBasalProfile_NewProfile(0x09, PumpHistoryEntryGroup.Basal, 2, 5, 145), //
    /**/EventUnknown_MM512_0x10(0x10, PumpHistoryEntryGroup.Unknown), // 29, 5, 0
    CalBGForPH(0x0a, "BG Capture", PumpHistoryEntryGroup.Glucose), //
    SensorAlert(0x0b, "SensorAlert", PumpHistoryEntryGroup.Alarm, 3, 5, 0), // Ian08
    ClearAlarm(0x0c, "ClearAlarm", PumpHistoryEntryGroup.Alarm, 2, 5, 0), // 2,5,4

    // Andy0d(0x0d, "Unknown", 2, 5, 0),

    ChangeBasalPattern(0x14, "Change Basal Pattern", PumpHistoryEntryGroup.Basal), //
    TempBasalDuration(0x16, "TempBasalDuration", PumpHistoryEntryGroup.Basal), //
    ChangeTime(0x17, "ChangeTime", PumpHistoryEntryGroup.Configuration), //
    NewTimeSet(0x18, "NewTimeSet", PumpHistoryEntryGroup.Notification), //
    LowBattery(0x19, "LowBattery", PumpHistoryEntryGroup.Notification), //
    BatteryChange(0x1a, "Battery Change", PumpHistoryEntryGroup.Notification), //
    SetAutoOff(0x1b, "SetAutoOff", PumpHistoryEntryGroup.Configuration), //
    PumpSuspend(0x1e, "Pump Suspend", PumpHistoryEntryGroup.Basal), //
    PumpResume(0x1f, "Pump Resume", PumpHistoryEntryGroup.Basal), //

    SelfTest(0x20, "SelfTest", PumpHistoryEntryGroup.Statistic), //
    Rewind(0x21, "Rewind", PumpHistoryEntryGroup.Prime), //
    ClearSettings(0x22, "ClearSettings", PumpHistoryEntryGroup.Configuration), // 8?
    ChangeChildBlockEnable(0x23, "ChangeChildBlockEnable", PumpHistoryEntryGroup.Configuration), // 8?
    ChangeMaxBolus(0x24, PumpHistoryEntryGroup.Configuration), // 8?
    /**/EventUnknown_MM522_0x25(0x25, PumpHistoryEntryGroup.Unknown), // 8?
    ToggleRemote(0x26, "EnableDisableRemote", PumpHistoryEntryGroup.Configuration, 2, 5, 14), // 2, 5, 14 V6:2,5,14
    ChangeRemoteId(0x27, "ChangeRemoteID", PumpHistoryEntryGroup.Configuration), // ??

    ChangeMaxBasal(0x2c, PumpHistoryEntryGroup.Configuration), //
    BolusWizardEnabled(0x2d, PumpHistoryEntryGroup.Configuration), // V3 ?
    /**/EventUnknown_MM512_0x2e(0x2e, PumpHistoryEntryGroup.Unknown), //
    /**/BolusWizard512(0x2f, PumpHistoryEntryGroup.Configuration), //
    UnabsorbedInsulin512(0x30, PumpHistoryEntryGroup.Statistic), //
    ChangeBGReminderOffset(0x31, PumpHistoryEntryGroup.Configuration), //
    ChangeAlarmClockTime(0x32, PumpHistoryEntryGroup.Configuration), //
    TempBasalRate(0x33, "Temp Basal Rate", PumpHistoryEntryGroup.Basal, 2, 5, 1), //
    LowReservoir(0x34, PumpHistoryEntryGroup.Notification), //
    ChangeAlarmClock(0x35, "Change Alarm Clock", PumpHistoryEntryGroup.Configuration), //
    ChangeMeterId(0x36, PumpHistoryEntryGroup.Configuration), //
    /**/EventUnknown_MM512_0x37(0x37, PumpHistoryEntryGroup.Unknown), // V:MM512
    /**/EventUnknown_MM512_0x38(0x38, PumpHistoryEntryGroup.Unknown), //
    BGReceived512(0x39, PumpHistoryEntryGroup.Glucose), //
    SensorStatus(0x3b, PumpHistoryEntryGroup.Glucose), //
    ChangeParadigmID(0x3c, PumpHistoryEntryGroup.Configuration, 2, 5, 14), // V3 ? V6: 2,5,14

    BGReceived(0x3f, "BG Received", PumpHistoryEntryGroup.Glucose, 2, 5, 3), // Ian3F
    JournalEntryMealMarker(0x40, PumpHistoryEntryGroup.Bolus, 2, 5, 2), //
    JournalEntryExerciseMarker(0x41, PumpHistoryEntryGroup.Bolus, 2, 5, 1), // ?? JournalEntryExerciseMarkerPumpEvent
    JournalEntryInsulinMarker(0x42, PumpHistoryEntryGroup.Bolus, 2, 5, 1), // ?? InsulinMarkerEvent
    JournalEntryOtherMarker(0x43, PumpHistoryEntryGroup.Bolus), //
    EnableSensorAutoCal(0x44, PumpHistoryEntryGroup.Glucose), //
    /**/EventUnknown_MM522_0x45(0x45, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x46(0x46, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x47(0x47, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x48(0x48, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x49(0x49, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x4a(0x4a, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x4b(0x4b, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x4c(0x4c, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_0x4d(0x4d, PumpHistoryEntryGroup.Unknown), // V5: 512: 7, 522: 8 ????NS
    /**/EventUnknown_MM512_0x4e(0x4e, PumpHistoryEntryGroup.Unknown), // /**/
    ChangeBolusWizardSetup(0x4f, PumpHistoryEntryGroup.Configuration, 2, 5, 32), //
    ChangeSensorSetup2(0x50, PumpHistoryEntryGroup.Configuration, 2, 5, 30), // Ian50
    /**/Sensor51(0x51, PumpHistoryEntryGroup.Unknown), //
    /**/Sensor52(0x52, PumpHistoryEntryGroup.Unknown), //
    ChangeSensorAlarmSilenceConfig(0x53, PumpHistoryEntryGroup.Configuration, 2, 5, 1), // 8 -
                                                                                        // ChangeSensorAlarmSilenceConfig
    /**/Sensor54(0x54, PumpHistoryEntryGroup.Unknown), // Ian54
    /**/Sensor55(0x55, PumpHistoryEntryGroup.Unknown), //
    ChangeSensorRateOfChangeAlertSetup(0x56, PumpHistoryEntryGroup.Configuration, 2, 5, 5), // 12
                                                                                            // ChangeSensorRateOfChangeAlertSetup
    ChangeBolusScrollStepSize(0x57, PumpHistoryEntryGroup.Configuration), //

    // V4
    // Andy58(0x58, "Unknown", 13, 5, 0), // TO DO is this one really there ???

    BolusWizardChange(0x5a, "BolusWizard", PumpHistoryEntryGroup.Configuration, 2, 5, 117), // V2: 522+[B=143]
    BolusWizardBolusEstimate(0x5b, "BolusWizardBolusEstimate", PumpHistoryEntryGroup.Configuration, 2, 5, 13), // 15 //
    UnabsorbedInsulin(0x5c, "UnabsorbedInsulinBolus", PumpHistoryEntryGroup.Statistic, 5, 0, 0), // head[1] -> body
                                                                                                 // length
    SaveSettings(0x5d, PumpHistoryEntryGroup.Configuration), //
    ChangeVariableBolus(0x5e, PumpHistoryEntryGroup.Configuration), //
    ChangeAudioBolus(0x5f, "EasyBolusEnabled", PumpHistoryEntryGroup.Configuration), // V3 ?
    ChangeBGReminderEnable(0x60, PumpHistoryEntryGroup.Configuration), // questionable60
    ChangeAlarmClockEnable(0x61, PumpHistoryEntryGroup.Configuration), //
    ChangeTempBasalType((byte)0x62, PumpHistoryEntryGroup.Configuration), // ChangeTempBasalTypePumpEvent
    ChangeAlarmNotifyMode(0x63, PumpHistoryEntryGroup.Configuration), //
    ChangeTimeFormat(0x64, PumpHistoryEntryGroup.Configuration), //
    ChangeReservoirWarningTime((byte)0x65, PumpHistoryEntryGroup.Configuration), //
    ChangeBolusReminderEnable(0x66, PumpHistoryEntryGroup.Configuration, 2, 5, 2), // 9
    ChangeBolusReminderTime((byte)0x67, PumpHistoryEntryGroup.Configuration, 2, 5, 2), // 9
    DeleteBolusReminderTime((byte)0x68, PumpHistoryEntryGroup.Configuration, 2, 5, 2), // 9
    BolusReminder(0x69, PumpHistoryEntryGroup.Configuration, 2, 5, 0), // Ian69
    DeleteAlarmClockTime(0x6a, "Delete Alarm Clock Time", PumpHistoryEntryGroup.Configuration, 2, 5, 7), // 14

    DailyTotals515(0x6c, "Daily Totals 515", PumpHistoryEntryGroup.Statistic, 0, 0, 36), //
    DailyTotals522(0x6d, "Daily Totals 522", PumpHistoryEntryGroup.Statistic, 1, 2, 41), // // hack1(0x6d, "hack1", 46,
                                                                                         // 5, 0), // 1,2,41
    DailyTotals523(0x6e, "Daily Totals 523", PumpHistoryEntryGroup.Statistic, 1, 2, 49), // 1102014-03-17T00:00:00
    ChangeCarbUnits((byte)0x6f, PumpHistoryEntryGroup.Configuration), //
    /**/EventUnknown_MM522_0x70((byte)0x70, PumpHistoryEntryGroup.Unknown, 2, 5, 1), //

    BasalProfileStart(0x7b, PumpHistoryEntryGroup.Basal, 2, 5, 3), // // 722
    ChangeWatchdogEnable((byte)0x7c, PumpHistoryEntryGroup.Configuration), //
    ChangeOtherDeviceID((byte)0x7d, PumpHistoryEntryGroup.Configuration, 2, 5, 30), //

    ChangeWatchdogMarriageProfile(0x81, PumpHistoryEntryGroup.Configuration, 2, 5, 5), // 12
    DeleteOtherDeviceID(0x82, PumpHistoryEntryGroup.Configuration, 2, 5, 5), //
    ChangeCaptureEventEnable(0x83, PumpHistoryEntryGroup.Configuration), //

    /**/EventUnknown_MM512_0x88(0x88, PumpHistoryEntryGroup.Unknown), //

    /**/EventUnknown_MM512_0x94(0x94, PumpHistoryEntryGroup.Unknown), //
    // IanA8(0xA8, "xx", 10, 5, 0), //

    // Andy90(0x90, "Unknown", 7, 5, 0),

    // AndyB4(0xb4, "Unknown", 7, 5, 0),
    // Andy4A(0x4a, "Unknown", 5, 5, 0),

    // head[1],
    // body[49] op[0x6e]

    /**/EventUnknown_MM522_0xE8(0xe8, PumpHistoryEntryGroup.Unknown, 2, 5, 25), //

    ReadOtherDevicesIDs(0xf0, "", PumpHistoryEntryGroup.Configuration), // ?
    ReadCaptureEventEnabled(0xf1, PumpHistoryEntryGroup.Configuration), // ?
    ChangeCaptureEventEnable2(0xf2, PumpHistoryEntryGroup.Configuration), // ?
    ReadOtherDevicesStatus(0xf3, PumpHistoryEntryGroup.Configuration), // ?

    TempBasalCombined(0xfe, "TempBasalCombined", PumpHistoryEntryGroup.Basal), //
    UnknownBasePacket(0xff, "Unknown Base Packet", PumpHistoryEntryGroup.Unknown);

    private static Map<Integer, PumpHistoryEntryType> opCodeMap = new HashMap<Integer, PumpHistoryEntryType>();

    static {
        for (PumpHistoryEntryType type : values()) {
            opCodeMap.put(type.opCode, type);
        }

        setSpecialRulesForEntryTypes();
    }

    private int opCode;
    private String description;
    private int headLength = 0;
    private int dateLength;
    // private MinimedDeviceType deviceType;
    private int bodyLength;
    private int totalLength;
    // special rules need to be put in list from highest to lowest (e.g.:
    // 523andHigher=12, 515andHigher=10 and default (set in cnstr) would be 8)
    private List<SpecialRule> specialRulesHead;
    private List<SpecialRule> specialRulesBody;
    private boolean hasSpecialRules = false;
    private PumpHistoryEntryGroup group = PumpHistoryEntryGroup.Unknown;


    // @Deprecated
    // PumpHistoryEntryType(int opCode, String name) {
    // this(opCode, name, 2, 5, 0);
    // }

    PumpHistoryEntryType(int opCode, String name, PumpHistoryEntryGroup group) {
        this(opCode, name, group, 2, 5, 0);
    }


    // @Deprecated
    // PumpHistoryEntryType(int opCode) {
    // this(opCode, null, null, 2, 5, 0);
    // }

    PumpHistoryEntryType(int opCode, PumpHistoryEntryGroup group) {
        this(opCode, null, group, 2, 5, 0);
    }


    // @Deprecated
    // PumpHistoryEntryType(int opCode, int head, int date, int body) {
    // this(opCode, null, null, head, date, body);
    // }

    PumpHistoryEntryType(int opCode, PumpHistoryEntryGroup group, int head, int date, int body) {
        this(opCode, null, group, head, date, body);
    }


    // @Deprecated
    // PumpHistoryEntryType(int opCode, String name, int head, DateFormat dateFormat, int body) {
    // this(opCode, name, head, dateFormat.getLength(), body);
    // }

    // @Deprecated
    // PumpHistoryEntryType(int opCode, String name, int head, int date, int body) {
    // this.opCode = (byte)opCode;
    // this.description = name;
    // this.headLength = head;
    // this.dateLength = date;
    // this.bodyLength = body;
    // this.totalLength = (head + date + body);
    // }

    PumpHistoryEntryType(int opCode, String name, PumpHistoryEntryGroup group, int head, int date, int body) {
        this.opCode = (byte)opCode;
        this.description = name;
        this.headLength = head;
        this.dateLength = date;
        this.bodyLength = body;
        this.totalLength = (head + date + body);
        this.group = group;
    }


    static void setSpecialRulesForEntryTypes() {
        EndResultTotals.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 3));
        Bolus.addSpecialRuleHead(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 8));
        // BolusWizardChange.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_522andHigher, 143));
        BolusWizardChange.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 143)); // V5:
                                                                                                                // 522
                                                                                                                // has
                                                                                                                // old
                                                                                                                // form
        BolusWizardBolusEstimate.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 15));
        BolusReminder.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 2));
    }


    public static PumpHistoryEntryType getByCode(int opCode) {
        if (opCodeMap.containsKey(opCode)) {
            return opCodeMap.get(opCode);
        } else {
            return PumpHistoryEntryType.UnknownBasePacket;
        }
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

    public static boolean isAAPSRelevantEntry(PumpHistoryEntryType entryType) {
        return (entryType == PumpHistoryEntryType.Bolus || // Treatments
            entryType == PumpHistoryEntryType.TempBasalRate || //
            entryType == PumpHistoryEntryType.TempBasalDuration || //

            entryType == PumpHistoryEntryType.Prime || // Pump Status Change
            entryType == PumpHistoryEntryType.PumpSuspend || //
            entryType == PumpHistoryEntryType.PumpResume || //
            entryType == PumpHistoryEntryType.Rewind || //
            entryType == PumpHistoryEntryType.NoDeliveryAlarm || // no delivery
            entryType == PumpHistoryEntryType.BasalProfileStart || //

            entryType == PumpHistoryEntryType.ChangeTime || // Time Change
            entryType == PumpHistoryEntryType.NewTimeSet || //

            entryType == PumpHistoryEntryType.ChangeBasalPattern || // Configuration
            entryType == PumpHistoryEntryType.ClearSettings || //
            entryType == PumpHistoryEntryType.SaveSettings || //
            entryType == PumpHistoryEntryType.ChangeMaxBolus || //
            entryType == PumpHistoryEntryType.ChangeMaxBasal || //
            entryType == PumpHistoryEntryType.ChangeTempBasalType || //

            entryType == PumpHistoryEntryType.ChangeBasalProfile_NewProfile || // Basal profile

            entryType == PumpHistoryEntryType.DailyTotals515 || // Daily Totals
            entryType == PumpHistoryEntryType.DailyTotals522 || //
            entryType == PumpHistoryEntryType.DailyTotals523 || //
        entryType == PumpHistoryEntryType.EndResultTotals);
    }


    public static boolean isRelevantEntry() {
        return true;
    }


    public int getCode() {
        return this.opCode;
    }


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
        if (isEmpty(specialRulesHead)) {
            specialRulesHead = new ArrayList<SpecialRule>();
        }

        specialRulesHead.add(rule);
        hasSpecialRules = true;
    }


    void addSpecialRuleBody(SpecialRule rule) {
        if (isEmpty(specialRulesBody)) {
            specialRulesBody = new ArrayList<SpecialRule>();
        }

        specialRulesBody.add(rule);
        hasSpecialRules = true;
    }


    public int getOpCode() {
        return opCode;
    }


    public String getDescription() {
        return this.description == null ? name() : this.description;
    }


    public int getHeadLength() {
        if (hasSpecialRules) {
            if (isNotEmpty(specialRulesHead)) {
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
            if (isNotEmpty(specialRulesBody)) {
                return determineSizeByRule(bodyLength, specialRulesBody);
            } else {
                return bodyLength;
            }
        } else {
            return bodyLength;
        }
    }


    private boolean isNotEmpty(List list) {
        return list != null && !list.isEmpty();
    }


    private boolean isEmpty(List list) {
        return list == null || list.isEmpty();
    }


    // byte[] dh = { 2, 3 };

    private int determineSizeByRule(int defaultValue, List<SpecialRule> rules) {
        int size = defaultValue;

        for (SpecialRule rule : rules) {
            if (MedtronicDeviceType.isSameDevice(MedtronicUtil.getMedtronicPumpModel(), rule.deviceType)) {
                size = rule.size;
                break;
            }
        }

        return size;
    }


    public PumpHistoryEntryGroup getGroup() {

        return group;
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

    public static class SpecialRule {

        MedtronicDeviceType deviceType;
        int size;


        public SpecialRule(MedtronicDeviceType deviceType, int size) {
            this.deviceType = deviceType;
            this.size = size;
        }
    }

}
