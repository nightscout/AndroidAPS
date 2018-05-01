package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class EnableDisableRemotePumpEvent extends TimeStampedRecord {
    public EnableDisableRemotePumpEvent() {
    }

    @Override
    public int getLength() { return 21; }

    @Override
    public String getShortTypeName() {
        return "Toggle Remote";
    }
}
