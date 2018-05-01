package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class RewindPumpEvent extends TimeStampedRecord {
    public RewindPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Rewind";
    }
}
