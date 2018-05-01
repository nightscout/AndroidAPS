package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class ChangeTimePumpEvent extends TimeStampedRecord {
    public ChangeTimePumpEvent() {

    }

    @Override
    public int getLength() { return 14; }

    @Override
    public String getShortTypeName() {
        return "Change Time";
    }
}
