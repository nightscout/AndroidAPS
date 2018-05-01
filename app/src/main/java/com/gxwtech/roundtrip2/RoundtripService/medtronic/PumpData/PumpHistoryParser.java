package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

import android.os.Bundle;
import android.util.Log;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpTimeStamp;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.TimeFormat;
import com.gxwtech.roundtrip2.util.ByteUtil;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by geoff on 6/18/16.
 *
 *
 *
 * This class is not in use -- it may become a replacement for the history object heirarchy parser
 *
 *
 *
 *
 *
 *
 *
 */
@Deprecated
public class PumpHistoryParser {
    private static final String TAG = "PumpHistoryParser";
    public PumpModel model;
    public ArrayList<PumpHistoryPage> pages = new ArrayList<>();
    public ArrayList<Bundle> entries = new ArrayList<>();
    public PumpHistoryParser(PumpModel model) {
        initOpcodes();
    }
    public void parsePage(byte[] rawData) {
        PumpHistoryPage page = new PumpHistoryPage(rawData,model,pages.size()+1);
        pages.add(page);
        if (page.isCRCValid() == false) {
            Log.e(TAG,"CRC16 for page " + page.getPageNumber() + " is invalid.");
        }
        if (page.data.length != 1024) {
            Log.w(TAG,String.format("Page %d has length %d, expected 1024",page.getPageNumber(),page.data.length));
        }
        int currentOffset = 0;
        boolean done = false;
        while (!done) {
            Bundle b = attemptParseRecord(page.getPageNumber(),currentOffset);
            if (b == null) {
                // then the parse failed.
            }
        }
    }

    static int asUINT8(byte b) {
        return (b < 0) ? b + 256 : b;
    }

    static double insulinDecode(int a, int b) {
        return ((a << 8) + b) / 40.0;
    }

    public Bundle attemptParseRecord(int pageNum, int offset) {
        byte[] dataPage = pages.get(pageNum).data;
        int opcode = pages.get(pageNum).data[offset];
        Bundle record = new Bundle();
        String typename = opCodeNames.get(opcode);
        if (typename != null) {
            record.putInt("opcode",opcode);
            record.putString("type",typename);
            record.putInt("page",pageNum);
            record.putInt("offset",offset);
            record.putString("indexer",String.format("%02d%04d",pageNum,offset));
        } else {
            return null;
        }
        int length = 0;
        int dateOffset = 0;
        PumpTimeStamp timestamp;
        byte[] data = ByteUtil.substring(dataPage,offset,dataPage.length - 2 - offset);
        switch (opcode) {
            case RECORD_TYPE_BolusNormal: {
                length = PumpModel.isLargerFormat(model) ? 13 : 9;
                if (length + offset > data.length) return null;
                record.putInt("length",length);
                double programmedAmount;
                double deliveredAmount;
                double unabsorbedInsulinTotal;
                int duration;
                String bolusType;
                if (PumpModel.isLargerFormat(model)) {
                    programmedAmount = insulinDecode(asUINT8(data[1]),asUINT8(data[2]));
                    deliveredAmount = insulinDecode(asUINT8(data[3]),asUINT8(data[4]));
                    unabsorbedInsulinTotal = insulinDecode(asUINT8(data[5]),asUINT8(data[6]));
                    duration = asUINT8(data[7]) * 30;
                    try {
                        timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, 8));
                    } catch (org.joda.time.IllegalFieldValueException e) {
                        timestamp = new PumpTimeStamp();
                    }
                } else {
                    programmedAmount = asUINT8(data[1]) / 10.0f;
                    deliveredAmount = asUINT8(data[2]) / 10.0f;
                    duration = asUINT8(data[3]) * 30;
                    unabsorbedInsulinTotal = 0;
                    try {
                        timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, 4));
                    } catch (org.joda.time.IllegalFieldValueException e) {
                        timestamp = new PumpTimeStamp();
                    }
                }
                bolusType = (duration > 0) ? "square" : "normal";
                record.putDouble("programmedAmount",programmedAmount);
                record.putDouble("deliveredAmount",deliveredAmount);
                record.putInt("duration",duration);
                record.putDouble("unabsorbedInsulinTotal",unabsorbedInsulinTotal);
                record.putString("bolusType",bolusType);
                record.putString("timestamp",timestamp.getLocalDateTime().toString());

            } break;
            case RECORD_TYPE_AlarmSensor:
                length = 8;
                if (length + offset > data.length) return null;
                record.putInt("length",length);
            case RECORD_TYPE_Resume:
            case RECORD_TYPE_AlarmClockReminder:
                length = 7;
                if (length + offset > data.length) return null;
                record.putInt("length",length);
                try {
                    timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, 2));
                } catch (org.joda.time.IllegalFieldValueException e) {
                    timestamp = new PumpTimeStamp();
                }
                record.putString("timestamp",timestamp.getLocalDateTime().toString());
            default:
                record.putInt("length",0);
                record.putString("comment","unparsed");
        }
        return record;
    }

    HashMap<Integer,String> opCodeNames = new HashMap<>();
    HashMap<String,Integer> opCodeNumbers = new HashMap<>();

    private void initOpcode(int number, String name) {
        opCodeNames.put(number,name);
        opCodeNumbers.put(name,number);
    }

    public static final int RECORD_TYPE_BolusNormal = 0x01;
    public static final int RECORD_TYPE_Prime = 0x03;
    public static final int RECORD_TYPE_PumpAlarm = 0x06;
    public static final int RECORD_TYPE_ResultDailyTotal = 0x07;
    public static final int RECORD_TYPE_ChangeBasalProfilePattern = 0x08;
    public static final int RECORD_TYPE_ChangeBasalProfile = 0x09;
    public static final int RECORD_TYPE_CalBgForPh = 0x0A;
    public static final int RECORD_TYPE_AlarmSensor = 0x0B;
    public static final int RECORD_TYPE_ClearAlarm = 0x0C;
    public static final int RECORD_TYPE_SelectBasalProfile = 0x14;
    public static final int RECORD_TYPE_TempBasalDuration = 0x16;
    public static final int RECORD_TYPE_ChangeTime = 0x17;
    public static final int RECORD_TYPE_NewTimeSet = 0x18;
    public static final int RECORD_TYPE_JournalEntryPumpLowBattery = 0x19;
    public static final int RECORD_TYPE_Battery = 0x1A;
    public static final int RECORD_TYPE_Suspend = 0x1E;
    public static final int RECORD_TYPE_Resume = 0x1F;
    public static final int RECORD_TYPE_Rewind = 0x21;
    public static final int RECORD_TYPE_ChangeChildBlockEnable = 0x23;
    public static final int RECORD_TYPE_ChangeMaxBolus = 0x24;
    public static final int RECORD_TYPE_EnableDisableRemote = 0x26;
    public static final int RECORD_TYPE_TempBasalRate = 0x33;
    public static final int RECORD_TYPE_JournalEntryPumpLowReservoir = 0x34;
    public static final int RECORD_TYPE_AlarmClockReminder = 0x35;
    public static final int RECORD_TYPE_BGReceived = 0x3F;
    public static final int RECORD_TYPE_JournalEntryExerciseMarker = 0x41;
    public static final int RECORD_TYPE_ChangeSensorSetup2 = 0x50;
    public static final int RECORD_TYPE_ChangeSensorRateOfChangeAlertSetup = 0x56;
    public static final int RECORD_TYPE_ChangeBolusScrollStepSize = 0x57;
    public static final int RECORD_TYPE_ChangeBolusWizardSetup = 0x5A;
    public static final int RECORD_TYPE_BolusWizardBolusEstimate = 0x5B;
    public static final int RECORD_TYPE_UnabsorbedInsulin = 0x5C;
    public static final int RECORD_TYPE_ChangeVariableBolus = 0x5e;
    public static final int RECORD_TYPE_ChangeAudioBolus = 0x5f;
    public static final int RECORD_TYPE_ChangeBGReminderEnable = 0x60;
    public static final int RECORD_TYPE_ChangeAlarmClockEnable = 0x61;
    public static final int RECORD_TYPE_ChangeTempBasalType = 0x62;
    public static final int RECORD_TYPE_ChangeAlarmNotifyMode = 0x63;
    public static final int RECORD_TYPE_ChangeTimeFormat = 0x64;
    public static final int RECORD_TYPE_ChangeReservoirWarningTime = 0x65;
    public static final int RECORD_TYPE_ChangeBolusReminderEnable = 0x66;
    public static final int RECORD_TYPE_ChangeBolusReminderTime = 0x67;
    public static final int RECORD_TYPE_DeleteBolusReminderTime = 0x68;
    public static final int RECORD_TYPE_DeleteAlarmClockTime = 0x6a;
    public static final int RECORD_TYPE_Model522ResultTotals = 0x6D;
    public static final int RECORD_TYPE_Sara6E = 0x6E;
    public static final int RECORD_TYPE_ChangeCarbUnits = 0x6f;
    public static final int RECORD_TYPE_BasalProfileStart = 0x7B;
    public static final int RECORD_TYPE_ChangeWatchdogEnable = 0x7c;
    public static final int RECORD_TYPE_ChangeOtherDeviceID = 0x7d;
    public static final int RECORD_TYPE_ChangeWatchdogMarriageProfile = 0x81;
    public static final int RECORD_TYPE_DeleteOtherDeviceID = 0x82;
    public static final int RECORD_TYPE_ChangeCaptureEventEnable = 0x83;

    private void initOpcodes() {
        initOpcode(0x01,"BolusNormal");
        initOpcode(0x03,"Prime");
        initOpcode(0x06,"PumpAlarm");
        initOpcode(0x07,"ResultDailyTotal");
        initOpcode(0x08,"ChangeBasalProfilePattern");
        initOpcode(0x09,"ChangeBasalProfile");
        initOpcode(0x0A,"CalBgForPh");
        initOpcode(0x0B,"AlarmSensor");
        initOpcode(0x0C,"ClearAlarm");
        //initOpcode(0x14,"SelectBasalProfile");
        initOpcode(0x16,"TempBasalDuration");
        initOpcode(0x17,"ChangeTime");
        //initOpcode(0x18,"NewTimeSet");
        initOpcode(0x19,"JournalEntryPumpLowBattery");
        initOpcode(0x1A,"Battery");
        initOpcode(0x1E,"Suspend");
        initOpcode(0x1F,"Resume");
        initOpcode(0x21,"Rewind");
        initOpcode(0x23,"ChangeChildBlockEnable");
        initOpcode(0x24,"ChangeMaxBolus");
        initOpcode(0x26,"EnableDisableRemote");
        initOpcode(0x33,"TempBasalRate");
        initOpcode(0x34,"JournalEntryPumpLowReservoir");
        initOpcode(0x35,"AlarmClockReminder");
        initOpcode(0x3F,"BGReceived");
        initOpcode(0x41,"JournalEntryExerciseMarker");
        initOpcode(0x50,"ChangeSensorSetup2");
        initOpcode(0x56,"ChangeSensorRateOfChangeAlertSetup");
        initOpcode(0x57,"ChangeBolusScrollStepSize");
        initOpcode(0x5A,"ChangeBolusWizardSetup");
        initOpcode(0x5B,"BolusWizardBolusEstimate");
        initOpcode(0x5C,"UnabsorbedInsulin");
        initOpcode(0x5e,"ChangeVariableBolus");
        initOpcode(0x5f,"ChangeAudioBolus");
        initOpcode(0x60,"ChangeBGReminderEnable");
        initOpcode(0x61,"ChangeAlarmClockEnable");
        initOpcode(0x62,"ChangeTempBasalType");
        initOpcode(0x63,"ChangeAlarmNotifyMode");
        initOpcode(0x64,"ChangeTimeFormat");
        initOpcode(0x65,"ChangeReservoirWarningTime");
        initOpcode(0x66,"ChangeBolusReminderEnable");
        initOpcode(0x67,"ChangeBolusReminderTime");
        initOpcode(0x68,"DeleteBolusReminderTime");
        initOpcode(0x6a,"DeleteAlarmClockTime");
        initOpcode(0x6D,"Model522ResultTotals");
        initOpcode(0x6E,"Sara6E");
        initOpcode(0x6f,"ChangeCarbUnits");
        initOpcode(0x7B,"BasalProfileStart");
        initOpcode(0x7c,"ChangeWatchdogEnable");
        initOpcode(0x7d,"ChangeOtherDeviceID");
        initOpcode(0x81,"ChangeWatchdogMarriageProfile");
        initOpcode(0x82,"DeleteOtherDeviceID");
        initOpcode(0x83,"ChangeCaptureEventEnable");
    }


}
