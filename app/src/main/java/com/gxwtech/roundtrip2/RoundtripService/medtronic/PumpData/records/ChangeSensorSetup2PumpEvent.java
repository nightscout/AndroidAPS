package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeSensorSetup2PumpEvent extends TimeStampedRecord {
    public ChangeSensorSetup2PumpEvent() {}

    @Override
    public int getLength() { return 37; }

    @Override
    public String getShortTypeName() {
        return "Ch Sensor Setup2";
    }

}
