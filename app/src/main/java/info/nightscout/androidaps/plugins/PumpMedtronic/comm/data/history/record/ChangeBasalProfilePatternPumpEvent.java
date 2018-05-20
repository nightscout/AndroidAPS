package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

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
