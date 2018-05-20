package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;


import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class AlarmSensorPumpEvent extends TimeStampedRecord {
    public AlarmSensorPumpEvent() {
    }

    @Override
    public int getLength() {
        return 8;
    }

    @Override
    public String getShortTypeName() {
        return "Alarm Sensor";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
