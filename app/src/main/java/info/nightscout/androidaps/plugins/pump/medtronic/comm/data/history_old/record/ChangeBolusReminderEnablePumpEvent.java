package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBolusReminderEnablePumpEvent extends TimeStampedRecord {

    public ChangeBolusReminderEnablePumpEvent() {
    }


    @Override
    public int getLength() {
        return 9;
    }


    @Override
    public String getShortTypeName() {
        return "Ch Bolus Rmndr Enable";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }

}
