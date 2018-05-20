package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
