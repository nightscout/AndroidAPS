package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeMaxBolusPumpEvent extends TimeStampedRecord {
    public ChangeMaxBolusPumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch Max Bolux";
    }
}
