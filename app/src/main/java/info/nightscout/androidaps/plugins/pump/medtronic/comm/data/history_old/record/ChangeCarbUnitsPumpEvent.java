package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

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
