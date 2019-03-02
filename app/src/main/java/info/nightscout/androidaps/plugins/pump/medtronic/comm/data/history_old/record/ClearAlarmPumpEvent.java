package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

@Deprecated
public class ClearAlarmPumpEvent extends TimeStampedRecord {

    public ClearAlarmPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Clear Alarm";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
