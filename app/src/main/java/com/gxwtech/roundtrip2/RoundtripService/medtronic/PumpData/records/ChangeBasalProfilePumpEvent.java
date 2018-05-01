package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class ChangeBasalProfilePumpEvent extends TimeStampedRecord {
    public ChangeBasalProfilePumpEvent() {
    }

    @Override
    public int getLength() { return 152; }

    @Override
    public String getShortTypeName() {
        return "Ch Basal Profile";
    }

}
