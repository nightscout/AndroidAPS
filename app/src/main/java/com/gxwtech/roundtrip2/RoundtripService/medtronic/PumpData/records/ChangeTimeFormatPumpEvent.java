package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class ChangeTimeFormatPumpEvent extends TimeStampedRecord {
    public ChangeTimeFormatPumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Ch Time Format";
    }
}
