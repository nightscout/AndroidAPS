package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
