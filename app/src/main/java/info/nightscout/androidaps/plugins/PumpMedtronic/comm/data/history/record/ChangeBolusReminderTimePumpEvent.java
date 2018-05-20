package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBolusReminderTimePumpEvent extends TimeStampedRecord {
    public ChangeBolusReminderTimePumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Ch Bolus Rmndr Time";
    }

    @Override
    public int getLength() {
        return 9;
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
