package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class DeleteBolusReminderTimePumpEvent extends TimeStampedRecord {

    public DeleteBolusReminderTimePumpEvent() {
    }


    @Override
    public int getLength() {
        return 9;
    }


    @Override
    public String getShortTypeName() {
        return "Del Bolus Rmndr Time";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
