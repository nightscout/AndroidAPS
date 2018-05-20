package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeCarbUnitsPumpEvent extends TimeStampedRecord {
    public ChangeCarbUnitsPumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Ch Carb Units";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
