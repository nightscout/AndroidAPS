package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBasalProfilePatternPumpEvent extends TimeStampedRecord {

    public ChangeBasalProfilePatternPumpEvent() {
    }


    @Override
    public int getLength() {
        return 152;
    }


    @Override
    public String getShortTypeName() {
        return "Ch Basal Prof Pat";
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }

}
