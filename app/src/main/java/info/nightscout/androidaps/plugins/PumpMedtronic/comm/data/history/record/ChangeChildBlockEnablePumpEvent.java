package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeChildBlockEnablePumpEvent extends TimeStampedRecord {
    public ChangeChildBlockEnablePumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Ch Child Block Ena";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
