package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class Model522ResultTotalsPumpEvent extends TimeStampedRecord {

    public Model522ResultTotalsPumpEvent() {
    }


    @Override
    public int getDatestampOffset() {
        return 1;
    }


    @Override
    public int getLength() {
        return 44;
    }


    @Override
    public String getShortTypeName() {
        return "M522 Result Totals";
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
