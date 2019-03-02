package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

public class ChangeAlarmNotifyModePumpEvent extends TimeStampedRecord {

    public ChangeAlarmNotifyModePumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Alarm Notify Mode";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }

}
