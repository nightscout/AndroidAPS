package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class JournalEntryPumpLowReservoirPumpEvent extends TimeStampedRecord {
    public JournalEntryPumpLowReservoirPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Low Reservoir";
    }
}
