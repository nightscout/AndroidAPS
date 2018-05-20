package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class RewindPumpEvent extends TimeStampedRecord {
    public RewindPumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Rewind";
    }

    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
