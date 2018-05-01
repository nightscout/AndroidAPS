package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class BatteryPumpEvent extends TimeStampedRecord {
    public BatteryPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Battery";
    }
}
