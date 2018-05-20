package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class DeleteAlarmClockTimePumpEvent extends TimeStampedRecord {
    public DeleteAlarmClockTimePumpEvent() {
    }

    @Override
    public int getLength() {
        return 14;
    }

    @Override
    public String getShortTypeName() {
        return "Del Alarm Clock Time";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
