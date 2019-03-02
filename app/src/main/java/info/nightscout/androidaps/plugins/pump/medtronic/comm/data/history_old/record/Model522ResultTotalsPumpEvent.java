package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

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
