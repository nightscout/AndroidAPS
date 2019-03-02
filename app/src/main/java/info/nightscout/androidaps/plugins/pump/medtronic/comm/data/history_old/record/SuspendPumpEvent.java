package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

public class SuspendPumpEvent extends TimeStampedRecord {

    public SuspendPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Suspend";
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
