package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class ResumePumpEvent extends TimeStampedRecord {
    public ResumePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Resume";
    }
}
