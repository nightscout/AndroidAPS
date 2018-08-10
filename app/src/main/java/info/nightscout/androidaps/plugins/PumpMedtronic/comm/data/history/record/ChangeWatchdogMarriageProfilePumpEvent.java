package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeWatchdogMarriageProfilePumpEvent extends TimeStampedRecord {

    public ChangeWatchdogMarriageProfilePumpEvent() {
    }


    @Override
    public int getLength() {
        return 12;
    }


    @Override
    public String getShortTypeName() {
        return "Ch WD Marriage";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }

}
