package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeReservoirWarningTimePumpEvent extends TimeStampedRecord {

    public ChangeReservoirWarningTimePumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Ch Res Warn Time";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
