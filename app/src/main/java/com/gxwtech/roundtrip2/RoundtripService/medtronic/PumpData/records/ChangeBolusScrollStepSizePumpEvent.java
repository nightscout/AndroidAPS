package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBolusScrollStepSizePumpEvent extends TimeStampedRecord {
    public ChangeBolusScrollStepSizePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch Bolus Scroll SS";
    }
}
