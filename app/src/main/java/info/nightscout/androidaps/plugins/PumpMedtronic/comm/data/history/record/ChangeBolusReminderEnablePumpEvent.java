package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
