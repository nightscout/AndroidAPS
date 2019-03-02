package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;

public class JournalEntryPumpLowReservoirPumpEvent extends TimeStampedRecord {

    public JournalEntryPumpLowReservoirPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Low Reservoir";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
