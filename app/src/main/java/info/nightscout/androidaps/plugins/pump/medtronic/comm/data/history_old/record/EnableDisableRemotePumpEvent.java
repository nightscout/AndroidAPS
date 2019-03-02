package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

public class EnableDisableRemotePumpEvent extends TimeStampedRecord {

    public EnableDisableRemotePumpEvent() {
    }


    @Override
    public int getLength() {
        return 21;
    }


    @Override
    public String getShortTypeName() {
        return "Toggle Remote";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
