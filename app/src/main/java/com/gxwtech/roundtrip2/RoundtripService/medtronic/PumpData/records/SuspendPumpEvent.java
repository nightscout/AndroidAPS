package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class SuspendPumpEvent extends TimeStampedRecord {
    public SuspendPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Suspend";
    }
}
