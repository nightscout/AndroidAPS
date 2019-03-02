package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
@Deprecated
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
