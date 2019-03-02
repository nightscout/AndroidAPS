package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeMaxBolusPumpEvent extends TimeStampedRecord {

    public ChangeMaxBolusPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Max Bolux";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
