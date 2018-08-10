package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class JournalEntryPumpLowBatteryPumpEvent extends TimeStampedRecord {

    public JournalEntryPumpLowBatteryPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Low Battery";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
