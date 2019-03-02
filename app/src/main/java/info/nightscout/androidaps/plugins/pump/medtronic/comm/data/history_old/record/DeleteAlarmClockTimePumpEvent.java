package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

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
