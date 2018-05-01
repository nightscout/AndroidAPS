package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by geoff on 5/28/15.
 */
public enum RecordTypeEnum {
    RECORD_TYPE_NULL((byte)0x00, null),
    RECORD_TYPE_BOLUSNORMAL((byte)0x01,BolusNormalPumpEvent.class),
    RECORD_TYPE_PRIME((byte)0x03,PrimePumpEvent.class),
    RECORD_TYPE_ALARMPUMP((byte)0x06,PumpAlarmPumpEvent.class),
    RECORD_TYPE_RESULTDAILYTOTAL((byte)0x07,ResultDailyTotalPumpEvent.class),
    RECORD_TYPE_CHANGEBASALPROFILEPATTERN((byte)0x08,ChangeBasalProfilePatternPumpEvent.class),
    RECORD_TYPE_CHANGEBASALPROFILE((byte)0x09,ChangeBasalProfilePumpEvent.class),
    RECORD_TYPE_CALBGFORPH((byte)0x0A,CalBgForPhPumpEvent.class),
    RECORD_TYPE_ALARMSENSOR((byte)0x0B,AlarmSensorPumpEvent.class),
    RECORD_TYPE_CLEARALARM((byte)0x0C,ClearAlarmPumpEvent.class),
    //RECORD_TYPE_SELECTBASALPROFILE((byte)0x14,SelectBasalProfile.class),
    RECORD_TYPE_TEMPBASALDURATION((byte)0x16,TempBasalDurationPumpEvent.class),
    RECORD_TYPE_CHANGETIME((byte)0x17,ChangeTimePumpEvent.class),
    RECORD_TYPE_NEWTIMESET((byte)0x18,NewTimeSet.class),
    RECORD_TYPE_JournalEntryPumpLowBattery((byte)0x19,JournalEntryPumpLowBatteryPumpEvent.class),
    RECORD_TYPE_BATTERY((byte)0x1A,BatteryPumpEvent.class),
    RECORD_TYPE_PUMPSUSPENDED((byte)0x1E,SuspendPumpEvent.class),
    RECORD_TYPE_PUMPRESUMED((byte)0x1F,ResumePumpEvent.class),
    RECORD_TYPE_REWIND((byte)0x21,RewindPumpEvent.class),
    RECORD_TYPE_CHANGECHILDBLOCKENABLE((byte)0x23,ChangeChildBlockEnablePumpEvent.class),
    RECORD_TYPE_CHANGEMAXBOLUS((byte)0x24,ChangeMaxBolusPumpEvent.class),
    RECORD_TYPE_ENABLEDISABLEREMOTE((byte)0x26,EnableDisableRemotePumpEvent.class),
    RECORD_TYPE_TEMPBASALRATE((byte)0x33,TempBasalRatePumpEvent.class),
    RECORD_TYPE_LOWRESERVOIR((byte)0x34,JournalEntryPumpLowReservoirPumpEvent.class),
    RECORD_TYPE_AlarmClockReminder((byte)0x35,AlarmClockReminderPumpEvent.class),
    RECORD_TYPE_BGRECEIVED((byte)0x3F,BGReceivedPumpEvent.class),
    RECORD_TYPE_JournalEntryExerciseMarker((byte)0x41,JournalEntryExerciseMarkerPumpEvent.class),
    RECORD_TYPE_Unknown7Byte_1((byte)0x42,Unknown7ByteEvent1.class),
    RECORD_TYPE_InsulinMarker((byte)0x43,InsulinMarkerEvent.class),
    RECORD_TYPE_CHANGESENSORSETUP2((byte)0x50,ChangeSensorSetup2PumpEvent.class),
    RECORD_TYPE_ChangeSensorRateOfChangeAlertSetup((byte)0x56,ChangeSensorRateOfChangeAlertSetupPumpEvent.class),
    RECORD_TYPE_ChangeBolusScrollStepSize((byte)0x57,ChangeBolusScrollStepSizePumpEvent.class),
    RECORD_TYPE_ChangeBolusWizardSetup((byte)0x5A,ChangeBolusWizardSetupPumpEvent.class),
    RECORD_TYPE_BolusWizardBolusEstimate((byte)0x5B,BolusWizardBolusEstimatePumpEvent.class),
    RECORD_TYPE_UNABSORBEDINSULIN((byte)0x5C,UnabsorbedInsulin.class),
    RECORD_TYPE_CHANGEVARIABLEBOLUS((byte)0x5e,ChangeVariableBolusPumpEvent.class),
    RECORD_TYPE_CHANGEAUDIOBOLUS((byte)0x5f,ChangeAudioBolusPumpEvent.class),
    RECORD_TYPE_ChangeBGReminderEnable((byte)0x60,ChangeBGReminderEnablePumpEvent.class),
    RECORD_TYPE_ChangeAlarmClockEnable((byte)0x61,ChangeAlarmClockEnablePumpEvent.class),
    RECORD_TYPE_ChangeTempBasalType((byte)0x62,ChangeTempBasalTypePumpEvent.class),
    RECORD_TYPE_ChangeAlarmNotifyMode((byte)0x63,ChangeAlarmNotifyModePumpEvent.class),
    RECORD_TYPE_ChangeTimeFormat((byte)0x64,ChangeTimeFormatPumpEvent.class),
    RECORD_TYPE_ChangeReservoirWarningTime((byte)0x65,ChangeReservoirWarningTimePumpEvent.class),
    RECORD_TYPE_ChangeBolusReminderEnable((byte)0x66,ChangeBolusReminderEnablePumpEvent.class),
    RECORD_TYPE_ChangeBolusReminderTime((byte)0x67,ChangeBolusReminderTimePumpEvent.class),
    RECORD_TYPE_DeleteBolusReminderTime((byte)0x68,DeleteBolusReminderTimePumpEvent.class),
    RECORD_TYPE_DeleteAlarmClockTime((byte)0x6a,DeleteAlarmClockTimePumpEvent.class),
    RECORD_TYPE_MODEL522RESULTTOTALS((byte)0x6D,Model522ResultTotalsPumpEvent.class),
    RECORD_TYPE_SARA6E((byte)0x6E,Sara6EPumpEvent.class),
    RECORD_TYPE_ChangeCarbUnits((byte)0x6f,ChangeCarbUnitsPumpEvent.class),
    RECORD_TYPE_BASALPROFILESTART((byte)0x7B,BasalProfileStart.class),
    RECORD_TYPE_ChangeWatchdogEnable((byte)0x7c,ChangeWatchdogEnablePumpEvent.class),
    RECORD_TYPE_CHANGEOTHERDEVICEID((byte)0x7d,ChangeOtherDeviceIDPumpEvent.class),
    RECORD_TYPE_ChangeWatchdogMarriageProfile((byte)0x81,ChangeWatchdogMarriageProfilePumpEvent.class),
    RECORD_TYPE_DeleteOtherDeviceID((byte)0x82,DeleteOtherDeviceIDPumpEvent.class),
    RECORD_TYPE_ChangeCaptureEventEnable((byte)0x83,ChangeCaptureEventEnablePumpEvent.class);


    private byte opcode;
    private Class mRecordClass;

    public byte opcode() {
        return opcode;
    }
    public Class recordClass() {
        return mRecordClass;
    }
    RecordTypeEnum(byte b,Class c) {
        opcode = b;
        mRecordClass = c;
    }
    public static RecordTypeEnum fromByte(byte b) {
        for(RecordTypeEnum en : RecordTypeEnum.values()) {
            if (en.opcode() == b) {
                return en;
            }
        }
        return RECORD_TYPE_NULL;
    }

    private static final String TAG = "RecordTypeEnum";
    public <T extends Record> T getRecordClassInstance(PumpModel model) {
        Constructor<T> ctor;
        T record = null;
        try {
            Class c = recordClass();
            if (c!=null) {
                ctor = recordClass().getConstructor();
                if (ctor != null) {
                    record = ctor.newInstance();
                    record.setPumpModel(model);
                }
            }
        } catch (NoSuchMethodException e) {
            // NOTE: these were all OR'd together, but android requires us to separate them.
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return record;
    }

    public static <T extends Record> T getRecordClassInstance(Bundle bundle, PumpModel model) {
        byte opcode = bundle.getByte("_opcode");
        RecordTypeEnum e = RecordTypeEnum.fromByte(opcode);
        return e.getRecordClassInstance(model);
    }

}
