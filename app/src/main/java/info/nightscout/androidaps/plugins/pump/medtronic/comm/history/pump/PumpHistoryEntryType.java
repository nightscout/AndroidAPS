package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */

public enum PumpHistoryEntryType // implements CodeEnum
{

    None(0, "None", PumpHistoryEntryGroup.Unknown, 1, 0, 0),

    Bolus(0x01, "Bolus", PumpHistoryEntryGroup.Bolus, 4, 5, 0), // 523+[H=8] 9/13

    Prime(0x03, "Prime", PumpHistoryEntryGroup.Prime, 5, 5, 0), //

    /**/EventUnknown_MM522_0x05((byte) 0x05, "Unknown Event 0x05", PumpHistoryEntryGroup.Unknown, 2, 5, 28), //
    NoDeliveryAlarm(0x06, "No Delivery", PumpHistoryEntryGroup.Alarm, 4, 5, 0), //
    EndResultTotals(0x07, "End Result Totals", PumpHistoryEntryGroup.Statistic, 5, 2, 0),
    ChangeBasalProfile_OldProfile(0x08, "Change Basal Profile (Old)", PumpHistoryEntryGroup.Basal, 2, 5, 145),
    ChangeBasalProfile_NewProfile(0x09, "Change Basal Profile (New)", PumpHistoryEntryGroup.Basal, 2, 5, 145), //
    /**/EventUnknown_MM512_0x10(0x10, "Unknown Event 0x10", PumpHistoryEntryGroup.Unknown), // 29, 5, 0
    CalBGForPH(0x0a, "BG Capture", PumpHistoryEntryGroup.Glucose), //
    SensorAlert(0x0b, "Sensor Alert", PumpHistoryEntryGroup.Alarm, 3, 5, 0), // Ian08
    ClearAlarm(0x0c, "Clear Alarm", PumpHistoryEntryGroup.Alarm, 2, 5, 0), // 2,5,4

    // Andy0d(0x0d, "Unknown", 2, 5, 0),

    ChangeBasalPattern(0x14, "Change Basal Pattern", PumpHistoryEntryGroup.Basal), //
    TempBasalDuration(0x16, "TBR Duration", PumpHistoryEntryGroup.Basal), //
    ChangeTime(0x17, "Change Time", PumpHistoryEntryGroup.Configuration), //
    NewTimeSet(0x18, "New Time Set", PumpHistoryEntryGroup.Notification), //
    LowBattery(0x19, "LowBattery", PumpHistoryEntryGroup.Notification), //
    BatteryChange(0x1a, "Battery Change", PumpHistoryEntryGroup.Notification), //
    SetAutoOff(0x1b, "Set Auto Off", PumpHistoryEntryGroup.Configuration), //
    Suspend(0x1e, "Suspend", PumpHistoryEntryGroup.Basal), //
    Resume(0x1f, "Resume", PumpHistoryEntryGroup.Basal), //
    SelfTest(0x20, "Self Test", PumpHistoryEntryGroup.Statistic), //
    Rewind(0x21, "Rewind", PumpHistoryEntryGroup.Prime), //
    ClearSettings(0x22, "Clear Settings", PumpHistoryEntryGroup.Configuration), //
    ChangeChildBlockEnable(0x23, "Change Child Block Enable", PumpHistoryEntryGroup.Configuration), //
    ChangeMaxBolus(0x24, "Change Max Bolus", PumpHistoryEntryGroup.Configuration), //
    /**/EventUnknown_MM522_0x25(0x25, "Unknown Event 0x25", PumpHistoryEntryGroup.Unknown), // 8?
    EnableDisableRemote(0x26, "Enable/Disable Remote", PumpHistoryEntryGroup.Configuration, 2, 5, 14), // 2, 5, 14 V6:2,5,14
    ChangeRemoteId(0x27, "Change Remote ID", PumpHistoryEntryGroup.Configuration), // ??

    ChangeMaxBasal(0x2c, "Change Max Basal", PumpHistoryEntryGroup.Configuration), //
    BolusWizardEnabled(0x2d, "Bolus Wizard Enabled", PumpHistoryEntryGroup.Configuration), // V3 ?
    /**/EventUnknown_MM512_0x2e(0x2e, "Unknown Event 0x2e", PumpHistoryEntryGroup.Unknown, 2, 5, 100), //
    BolusWizard512(0x2f, "Bolus Wizard (512)", PumpHistoryEntryGroup.Bolus, 2, 5, 12), //
    UnabsorbedInsulin512(0x30, "Unabsorbed Insulin (512)", PumpHistoryEntryGroup.Statistic, 5, 0, 0), // FIXME
    ChangeBGReminderOffset(0x31, "Change BG Reminder Offset", PumpHistoryEntryGroup.Configuration), //
    ChangeAlarmClockTime(0x32, "Change Alarm Clock Time", PumpHistoryEntryGroup.Configuration), //
    TempBasalRate(0x33, "TBR Rate", PumpHistoryEntryGroup.Basal, 2, 5, 1), //
    LowReservoir(0x34, "Low Reservoir", PumpHistoryEntryGroup.Notification), //
    ChangeAlarmClock(0x35, "Change Alarm Clock", PumpHistoryEntryGroup.Configuration), //
    ChangeMeterId(0x36, "Change Meter ID", PumpHistoryEntryGroup.Configuration), //
    /**/EventUnknown_MM512_0x37(0x37, "Unknown Event 0x37", PumpHistoryEntryGroup.Unknown), // V:MM512
    /**/EventUnknown_MM512_0x38(0x38, "Unknown Event 0x38", PumpHistoryEntryGroup.Unknown), //
    BGReceived512(0x39, "BG Received (512)", PumpHistoryEntryGroup.Glucose), //
    /**/EventUnknown_MM512_0x3a(0x3a, "Unknown Event 0x3a", PumpHistoryEntryGroup.Unknown), //
    SensorStatus(0x3b, "Sensor Status", PumpHistoryEntryGroup.Glucose), //
    ChangeParadigmID(0x3c, "Change Paradigm ID", PumpHistoryEntryGroup.Configuration, 2, 5, 14), // V3 ? V6: 2,5,14 ?? is it this length or just 7
    EventUnknown_MM512_0x3D(0x3d, "Unknown Event 0x3D", PumpHistoryEntryGroup.Unknown), //
    EventUnknown_MM512_0x3E(0x3e, "Unknown Event 0x3E", PumpHistoryEntryGroup.Unknown), //
    BGReceived(0x3f, "BG Received", PumpHistoryEntryGroup.Glucose, 2, 5, 3), // Ian3F
    JournalEntryMealMarker(0x40, "Meal Marker", PumpHistoryEntryGroup.Bolus, 2, 5, 2), // is size just 7??? V6
    JournalEntryExerciseMarker(0x41, "Exercise Marker", PumpHistoryEntryGroup.Bolus, 2, 5, 1), // ??
    JournalEntryInsulinMarker(0x42, "Insulin Marker", PumpHistoryEntryGroup.Bolus, 2, 5, 0), // V6 = body(0)/was=1
    JournalEntryOtherMarker(0x43, "Other Marker", PumpHistoryEntryGroup.Bolus, 2, 5, 1), // V6 = body(1)/was=0
    EnableSensorAutoCal(0x44, "Enable Sensor AutoCal", PumpHistoryEntryGroup.Glucose), //
    /**/EventUnknown_MM522_0x45(0x45, "Unknown Event 0x45", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x46(0x46, "Unknown Event 0x46", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x47(0x47, "Unknown Event 0x47", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x48(0x48, "Unknown Event 0x48", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x49(0x49, "Unknown Event 0x49", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x4a(0x4a, "Unknown Event 0x4a", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x4b(0x4b, "Unknown Event 0x4b", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_MM522_0x4c(0x4c, "Unknown Event 0x4c", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //
    /**/EventUnknown_0x4d(0x4d, "Unknown Event 0x4d", PumpHistoryEntryGroup.Unknown), // V5: 512: 7, 522: 8 ????NS
    /**/EventUnknown_MM512_0x4e(0x4e, "Unknown Event 0x4e", PumpHistoryEntryGroup.Unknown), // /**/
    ChangeBolusWizardSetup512(0x4f, "Bolus Wizard Setup (512)", PumpHistoryEntryGroup.Configuration, 2, 5, 32), //
    ChangeSensorSetup2(0x50, "Sensor Setup2", PumpHistoryEntryGroup.Configuration, 2, 5, 30), // Ian50
    /**/Sensor_0x51(0x51, "Unknown Event 0x51", PumpHistoryEntryGroup.Unknown), //
    /**/Sensor_0x52(0x52, "Unknown Event 0x52", PumpHistoryEntryGroup.Unknown), //
    ChangeSensorAlarmSilenceConfig(0x53, "Sensor Alarm Silence Config", PumpHistoryEntryGroup.Configuration, 2, 5, 1), // 8

    /**/Sensor_0x54(0x54, "Unknown Event 0x54", PumpHistoryEntryGroup.Unknown), // Ian54
    /**/Sensor_0x55(0x55, "Unknown Event 0x55", PumpHistoryEntryGroup.Unknown), //
    ChangeSensorRateOfChangeAlertSetup(0x56, "Sensor Rate Of Change Alert Setup", PumpHistoryEntryGroup.Configuration, 2, 5, 5), // 12
    ChangeBolusScrollStepSize(0x57, "Change Bolus Scroll Step Size", PumpHistoryEntryGroup.Configuration), //

    // V4
    // Andy58(0x58, "Unknown", 13, 5, 0), // TO DO is this one really there ???

    ChangeBolusWizardSetup(0x5a, "Bolus Wizard Setup (512)", PumpHistoryEntryGroup.Configuration, 2, 5, 137), // V2: 522+[B=143] // V6 124 -> 144
    BolusWizard(0x5b, "Bolus Wizard Estimate", PumpHistoryEntryGroup.Configuration, 2, 5, 13), // 15 //
    UnabsorbedInsulin(0x5c, "Unabsorbed Insulin", PumpHistoryEntryGroup.Statistic, 5, 0, 0), // head[1] -> body
    SaveSettings(0x5d, "Save Settings", PumpHistoryEntryGroup.Configuration), //
    ChangeVariableBolus(0x5e, "Change Variable Bolus", PumpHistoryEntryGroup.Configuration), //
    ChangeAudioBolus(0x5f, "Easy Bolus Enabled", PumpHistoryEntryGroup.Configuration), // V3 ?
    ChangeBGReminderEnable(0x60, "BG Reminder Enable", PumpHistoryEntryGroup.Configuration), // questionable60
    ChangeAlarmClockEnable(0x61, "Alarm Clock Enable", PumpHistoryEntryGroup.Configuration), //
    ChangeTempBasalType((byte) 0x62, "Change Basal Type", PumpHistoryEntryGroup.Configuration), // ChangeTempBasalTypePumpEvent
    ChangeAlarmNotifyMode(0x63, "Change Alarm Notify Mode", PumpHistoryEntryGroup.Configuration), //
    ChangeTimeFormat(0x64, "Change Time Format", PumpHistoryEntryGroup.Configuration), //
    ChangeReservoirWarningTime((byte) 0x65, "Change Reservoir Warning Time", PumpHistoryEntryGroup.Configuration), //
    ChangeBolusReminderEnable(0x66, "Change Bolus Reminder Enable", PumpHistoryEntryGroup.Configuration, 2, 5, 2), // 9
    ChangeBolusReminderTime((byte) 0x67, "Change Bolus Reminder Time", PumpHistoryEntryGroup.Configuration, 2, 5, 2), // 9
    DeleteBolusReminderTime((byte) 0x68, "Delete Bolus Reminder Time", PumpHistoryEntryGroup.Configuration, 2, 5, 2), // 9
    BolusReminder(0x69, "Bolus Reminder", PumpHistoryEntryGroup.Configuration, 2, 5, 0), // Ian69
    DeleteAlarmClockTime(0x6a, "Delete Alarm Clock Time", PumpHistoryEntryGroup.Configuration, 2, 5, 7), // 14

    DailyTotals515(0x6c, "Daily Totals (515)", PumpHistoryEntryGroup.Statistic, 1, 2, 35), // v4: 0,0,36. v5: 1,2,33
    DailyTotals522(0x6d, "Daily Totals (522)", PumpHistoryEntryGroup.Statistic, 1, 2, 41), //
    DailyTotals523(0x6e, "Daily Totals (523)", PumpHistoryEntryGroup.Statistic, 1, 2, 49), // 1102014-03-17T00:00:00
    ChangeCarbUnits((byte) 0x6f, "Change Carb Units", PumpHistoryEntryGroup.Configuration), //
    /**/EventUnknown_MM522_0x70((byte) 0x70, "Unknown Event 0x70", PumpHistoryEntryGroup.Unknown, 2, 5, 1), //

    BasalProfileStart(0x7b, "Basal Profile Start", PumpHistoryEntryGroup.Basal, 2, 5, 3), // // 722
    ChangeWatchdogEnable((byte) 0x7c, "Change Watchdog Enable", PumpHistoryEntryGroup.Configuration), //
    ChangeOtherDeviceID((byte) 0x7d, "Change Other Device ID", PumpHistoryEntryGroup.Configuration, 2, 5, 30), //

    ChangeWatchdogMarriageProfile(0x81, "Change Watchdog Marriage Profile", PumpHistoryEntryGroup.Configuration, 2, 5, 5), // 12
    DeleteOtherDeviceID(0x82, "Delete Other Device ID", PumpHistoryEntryGroup.Configuration, 2, 5, 5), //
    ChangeCaptureEventEnable(0x83, "Change Capture Event Enable", PumpHistoryEntryGroup.Configuration), //

    /**/EventUnknown_MM512_0x88(0x88, "Unknown Event 0x88", PumpHistoryEntryGroup.Unknown), //

    /**/EventUnknown_MM512_0x94(0x94, "Unknown Event 0x94", PumpHistoryEntryGroup.Unknown), //
    // IanA8(0xA8, "xx", 10, 5, 0), //

    // Andy90(0x90, "Unknown", 7, 5, 0),

    // AndyB4(0xb4, "Unknown", 7, 5, 0),
    // Andy4A(0x4a, "Unknown", 5, 5, 0),

    // head[1],
    // body[49] op[0x6e]

    /**/EventUnknown_MM522_0xE8(0xe8, "Unknown Event 0xE8", PumpHistoryEntryGroup.Unknown, 2, 5, 25), //

    ReadOtherDevicesIDs(0xf0, "Read Other Devices IDs", PumpHistoryEntryGroup.Configuration), // ?
    ReadCaptureEventEnabled(0xf1, "Read Capture Event Enabled", PumpHistoryEntryGroup.Configuration), // ?
    ChangeCaptureEventEnable2(0xf2, "Change Capture Event Enable2", PumpHistoryEntryGroup.Configuration), // ?
    ReadOtherDevicesStatus(0xf3, "Read Other Devices Status", PumpHistoryEntryGroup.Configuration), // ?

    TempBasalCombined(0xfe, "TBR", PumpHistoryEntryGroup.Basal), //
    UnknownBasePacket(0xff, "Unknown Base Packet", PumpHistoryEntryGroup.Unknown);

    private static Map<Integer, PumpHistoryEntryType> opCodeMap = new HashMap<Integer, PumpHistoryEntryType>();
    private static PumpHistoryEntryType tddType;

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
    private static Object TDDType;


    PumpHistoryEntryType(int opCode, String name, PumpHistoryEntryGroup group) {
        this(opCode, name, group, 2, 5, 0);
    }


    PumpHistoryEntryType(int opCode, PumpHistoryEntryGroup group) {
        this(opCode, null, group, 2, 5, 0);
    }


    PumpHistoryEntryType(int opCode, PumpHistoryEntryGroup group, int head, int date, int body) {
        this(opCode, null, group, head, date, body);
    }


    PumpHistoryEntryType(int opCode, String name, PumpHistoryEntryGroup group, int head, int date, int body) {
        this.opCode = (byte) opCode;
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
        //ChangeBolusWizardSetup.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 137)); // V5:
        // 522
        // has
        // old
        // form
        BolusWizard.addSpecialRuleBody(new SpecialRule(MedtronicDeviceType.Medtronic_523andHigher, 15));
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
                entryType == PumpHistoryEntryType.Suspend || //
                entryType == PumpHistoryEntryType.Resume || //
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
