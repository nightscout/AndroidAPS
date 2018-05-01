package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class ClearAlarmPumpEvent extends TimeStampedRecord {
    public ClearAlarmPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Clear Alarm";
    }
}
