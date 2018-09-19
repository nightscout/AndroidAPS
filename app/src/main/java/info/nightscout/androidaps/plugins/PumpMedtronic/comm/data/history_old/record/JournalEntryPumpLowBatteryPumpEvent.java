package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history_old.TimeStampedRecord;

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
