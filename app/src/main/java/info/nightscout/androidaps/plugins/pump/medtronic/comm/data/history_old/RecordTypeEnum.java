package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.AlarmClockReminderPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.AlarmSensorPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.BGReceivedPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.BasalProfileStart;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.BatteryPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.BolusNormalPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.BolusWizardBolusEstimatePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.CalBgForPhPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeAlarmClockEnablePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeAlarmNotifyModePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeAudioBolusPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBGReminderEnablePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBasalProfilePatternPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBasalProfilePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBolusReminderEnablePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBolusReminderTimePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBolusScrollStepSizePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeBolusWizardSetupPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeCaptureEventEnablePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeCarbUnitsPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeChildBlockEnablePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeMaxBolusPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeOtherDeviceIDPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeReservoirWarningTimePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeSensorRateOfChangeAlertSetupPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeSensorSetup2PumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeTempBasalTypePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeTimeFormatPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeTimePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeVariableBolusPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeWatchdogEnablePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ChangeWatchdogMarriageProfilePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ClearAlarmPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.DeleteAlarmClockTimePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.DeleteBolusReminderTimePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.DeleteOtherDeviceIDPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.EnableDisableRemotePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.IgnoredHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.InsulinMarkerEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.JournalEntryExerciseMarkerPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.JournalEntryPumpLowBatteryPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.JournalEntryPumpLowReservoirPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.Model522ResultTotalsPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.NewTimeSet;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.PrimePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.PumpAlarmPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ResultDailyTotalPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.ResumePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.RewindPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.SuspendPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.TempBasalDurationPumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.TempBasalRatePumpEvent;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.UnabsorbedInsulin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record.Unknown7ByteEvent1;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

/**
 * Created by geoff on 5/28/15.
 */
@Deprecated
public enum RecordTypeEnum {

    Null((byte)0x00, null, 0), //

    // Good Events
    BolusNormal(0x01, BolusNormalPumpEvent.class), // OK
    Prime((byte)0x03, PrimePumpEvent.class), // OK
    AlarmPump((byte)0x06, PumpAlarmPumpEvent.class), //
    ResultDailyTotal((byte)0x07, ResultDailyTotalPumpEvent.class), // OK
    ChangeBasalProfile_old_profile((byte)0x08, ChangeBasalProfilePatternPumpEvent.class), // OK
    ChangeBasalProfile_new_profile((byte)0x09, ChangeBasalProfilePumpEvent.class), // OK

    CalBgForPh((byte)0x0A, CalBgForPhPumpEvent.class), //
    AlarmSensor((byte)0x0B, AlarmSensorPumpEvent.class), //
    ClearAlarm((byte)0x0C, ClearAlarmPumpEvent.class), //
    SelectBasalProfile((byte)0x14, IgnoredHistoryEntry.class, 7), // OK
    TempBasalDuration((byte)0x16, TempBasalDurationPumpEvent.class), // OK
    ChangeTime((byte)0x17, ChangeTimePumpEvent.class), // OK
    NewTimeSet((byte)0x18, NewTimeSet.class), // OK

    JournalEntryPumpLowBattery((byte)0x19, JournalEntryPumpLowBatteryPumpEvent.class), //
    RECORD_TYPE_BATTERY((byte)0x1A, BatteryPumpEvent.class), //
    SetAutoOff(0x1b, 7), //
    Suspend((byte)0x1E, SuspendPumpEvent.class), // OK
    Resume((byte)0x1F, ResumePumpEvent.class), // OK
    SelfTest(0x20, 7), //
    Rewind((byte)0x21, RewindPumpEvent.class), //
    ClearSettings(0x22, 7), //
    ChangeChildBlockEnable((byte)0x23, ChangeChildBlockEnablePumpEvent.class), //
    ChangeMaxBolus((byte)0x24, ChangeMaxBolusPumpEvent.class), //
    EnableDisableRemote((byte)0x26, EnableDisableRemotePumpEvent.class), //
    ChangeMaxBasal(0x2c, 7), //
    EnableBolusWizard(0x2d, 7), //
    Andy2E(0x2e, 7), //
    Andy2F(0x2f, 7), //
    Andy30(0x30, 7), //
    ChangeBGReminderOffset(0x31, 7), //
    ChangeAlarmClockTime(0x32, 7), //
    tempBasal((byte)0x33, TempBasalRatePumpEvent.class), //
    journalEntryPumpLowReservoir((byte)0x34, JournalEntryPumpLowReservoirPumpEvent.class), //
    AlarmClockReminder((byte)0x35, AlarmClockReminderPumpEvent.class), //
    ChangeMeterId(0x36, 7), // 715 = 21 ??
    MM512_Event_0x37(0x37, 7), //
    MM512_Event_0x38(0x38, 7), //
    MM512_Event_0x39(0x39, 7), //
    MM512_Event_0x3A(0x3A, 7), //
    MM512_Event_0x3B(0x3b, 7), // Questionable3b
    changeParadigmLinkID(0x3c, 7), //
    MM512_Event_0x3D(0x3D, 7), //
    MM512_Event_0x3E(0x3e, 7), //
    bgReceived((byte)0x3F, BGReceivedPumpEvent.class), //
    JournalEntryMealMarker(0x40, 7), //
    JournalEntryExerciseMarker((byte)0x41, JournalEntryExerciseMarkerPumpEvent.class), //
    JournalEntryInsulinMarker((byte)0x42, Unknown7ByteEvent1.class), //
    journalEntryOtherMarker((byte)0x43, InsulinMarkerEvent.class), //

    MM512_Event_0x44(0x44, 7), //
    MM512_Event_0x45(0x45, 7), //
    MM512_Event_0x46(0x46, 7), //
    MM512_Event_0x47(0x47, 7), //
    MM512_Event_0x48(0x48, 7), //
    MM512_Event_0x49(0x49, 7), //
    MM512_Event_0x4a(0x4a, 7), //
    MM512_Event_0x4b(0x4b, 7), //
    MM512_Event_0x4c(0x4c, 7), //
    MM512_Event_0x4d(0x4d, 7), //
    MM512_Event_0x4e(0x4e, 7), //

    // case changeBolusWizardSetup = 0x4f, 7), //

    changeSensorSetup2((byte)0x50, ChangeSensorSetup2PumpEvent.class), //
    // case restoreMystery51 = 0x51, 7), //
    // case restoreMystery52 = 0x52, 7), //
    // case changeSensorAlarmSilenceConfig = 0x53, 7), //
    // case restoreMystery54 = 0x54, 7), //
    // case restoreMystery55 = 0x55, 7), //
    ChangeSensorRateOfChangeAlertSetup((byte)0x56, ChangeSensorRateOfChangeAlertSetupPumpEvent.class), //
    ChangeBolusScrollStepSize((byte)0x57, ChangeBolusScrollStepSizePumpEvent.class), //
    ChangeBolusWizardSetup((byte)0x5A, ChangeBolusWizardSetupPumpEvent.class), //
    BolusWizardBolusEstimate((byte)0x5B, BolusWizardBolusEstimatePumpEvent.class), //
    unabsorbedInsulin((byte)0x5C, UnabsorbedInsulin.class), //
    // case saveSettings = 0x5d, 7), //
    changeVariableBolus((byte)0x5e, ChangeVariableBolusPumpEvent.class), //
    changeAudioBolus((byte)0x5f, ChangeAudioBolusPumpEvent.class), //
    ChangeBGReminderEnable((byte)0x60, ChangeBGReminderEnablePumpEvent.class), //
    ChangeAlarmClockEnable((byte)0x61, ChangeAlarmClockEnablePumpEvent.class), //

    ChangeTempBasalType((byte)0x62, ChangeTempBasalTypePumpEvent.class), //
    ChangeAlarmNotifyMode((byte)0x63, ChangeAlarmNotifyModePumpEvent.class), //
    ChangeTimeFormat((byte)0x64, ChangeTimeFormatPumpEvent.class), //
    ChangeReservoirWarningTime((byte)0x65, ChangeReservoirWarningTimePumpEvent.class), //
    ChangeBolusReminderEnable((byte)0x66, ChangeBolusReminderEnablePumpEvent.class), //
    ChangeBolusReminderTime((byte)0x67, ChangeBolusReminderTimePumpEvent.class), //
    DeleteBolusReminderTime((byte)0x68, DeleteBolusReminderTimePumpEvent.class), //
    // case bolusReminder = 0x69, 7), //
    DeleteAlarmClockTime((byte)0x6a, DeleteAlarmClockTimePumpEvent.class), //
    DailyTotal515(0x6c, 38), // FIXME
    dailyTotal522((byte)0x6D, Model522ResultTotalsPumpEvent.class), //
    dailyTotal523((byte)0x6E, IgnoredHistoryEntry.class, 52), // Sara6E // FIXME
    ChangeCarbUnits((byte)0x6f, ChangeCarbUnitsPumpEvent.class), //
    basalProfileStart((byte)0x7B, BasalProfileStart.class), //
    ChangeWatchdogEnable((byte)0x7c, ChangeWatchdogEnablePumpEvent.class), //
    ChangeOtherDeviceID((byte)0x7d, ChangeOtherDeviceIDPumpEvent.class), //
    ChangeWatchdogMarriageProfile((byte)0x81, ChangeWatchdogMarriageProfilePumpEvent.class), //
    DeleteOtherDeviceID((byte)0x82, DeleteOtherDeviceIDPumpEvent.class), //
    ChangeCaptureEventEnable((byte)0x83, ChangeCaptureEventEnablePumpEvent.class),

    // Irelevant records (events that don't concern us for AAPS usage)

    ;

    private static final String TAG = "RecordTypeEnum";
    private static Map<Byte, RecordTypeEnum> mapByOpCode = null;
    private byte opcode;
    private Class mRecordClass;
    private int length;
    private String shortTypeName;


    RecordTypeEnum(int b, Class c) {
        opcode = (byte)b;
        mRecordClass = c;
    }


    RecordTypeEnum(int b, Class c, int length) {
        opcode = (byte)b;
        mRecordClass = c;
        this.length = length;
    }


    RecordTypeEnum(int b, int length) {
        this(b, IgnoredHistoryEntry.class, length);
    }


    public static RecordTypeEnum fromByte(byte b) {
        for (RecordTypeEnum en : RecordTypeEnum.values()) {
            if (en.opcode() == b) {
                return en;
            }
        }
        return Null;
    }


    public static <T extends Record> T getRecordClassInstance(Bundle bundle, MedtronicDeviceType model) {
        byte opcode = bundle.getByte("_opcode");
        RecordTypeEnum e = RecordTypeEnum.fromByte(opcode);
        return e.getRecordClassInstance(model);
    }


    public byte opcode() {
        return opcode;
    }


    public Class recordClass() {
        return mRecordClass;
    }


    public <T extends Record> T getRecordClassInstance(MedtronicDeviceType model) {
        Constructor<T> ctor;
        T record = null;
        try {
            Class c = recordClass();
            if (c != null) {
                ctor = recordClass().getConstructor();
                if (ctor != null) {
                    record = ctor.newInstance();
                    record.setPumpModel(model);

                    // if this is IgnoredHistoryEntry we need to set type so that we get correct length and name
                    if (record instanceof IgnoredHistoryEntry) {
                        IgnoredHistoryEntry he = (IgnoredHistoryEntry)record;
                        he.init(this);
                    }
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


    public int getLength() {
        return length;
    }


    public void setLength(int length) {
        this.length = length;
    }


    public String getShortTypeName() {
        return shortTypeName;
    }


    public void setShortTypeName(String shortTypeName) {
        this.shortTypeName = shortTypeName;
    }
}
