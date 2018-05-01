package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class JournalEntryPumpLowBatteryPumpEvent extends TimeStampedRecord {
    public JournalEntryPumpLowBatteryPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Low Battery";
    }
}
