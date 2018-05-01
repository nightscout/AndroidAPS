package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBasalProfilePatternPumpEvent extends TimeStampedRecord {
    public ChangeBasalProfilePatternPumpEvent() {}

    @Override
    public int getLength() {
        return 152;
    }

    @Override
    public String getShortTypeName() {
        return "Ch Basal Prof Pat";
    }

}
