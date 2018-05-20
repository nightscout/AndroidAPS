package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
