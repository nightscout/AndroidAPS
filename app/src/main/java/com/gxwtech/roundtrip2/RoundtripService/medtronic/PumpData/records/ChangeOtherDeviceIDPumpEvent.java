package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class ChangeOtherDeviceIDPumpEvent extends TimeStampedRecord {

    public ChangeOtherDeviceIDPumpEvent() {
    }

    @Override
    public int getLength() { return 37; }

    @Override
    public String getShortTypeName() {
        return "Ch Other Dev ID";
    }

}
