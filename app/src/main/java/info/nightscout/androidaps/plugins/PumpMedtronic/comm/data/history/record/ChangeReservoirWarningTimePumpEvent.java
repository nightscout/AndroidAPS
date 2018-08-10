package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
