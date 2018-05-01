package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeChildBlockEnablePumpEvent extends TimeStampedRecord {
    public ChangeChildBlockEnablePumpEvent(){}

    @Override
    public String getShortTypeName() {
        return "Ch Child Block Ena";
    }
}
