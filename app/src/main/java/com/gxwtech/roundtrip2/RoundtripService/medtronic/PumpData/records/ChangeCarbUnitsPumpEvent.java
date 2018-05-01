package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeCarbUnitsPumpEvent extends TimeStampedRecord {
    public ChangeCarbUnitsPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch Carb Units";
    }
}
