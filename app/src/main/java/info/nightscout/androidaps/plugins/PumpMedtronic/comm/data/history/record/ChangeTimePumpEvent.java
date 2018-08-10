package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class ChangeTimePumpEvent extends TimeStampedRecord {

    public ChangeTimePumpEvent() {

    }


    @Override
    public int getLength() {
        return 14;
    }


    @Override
    public String getShortTypeName() {
        return "Change Time";
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
