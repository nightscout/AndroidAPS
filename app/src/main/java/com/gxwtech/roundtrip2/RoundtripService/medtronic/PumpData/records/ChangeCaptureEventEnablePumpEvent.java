package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeCaptureEventEnablePumpEvent extends TimeStampedRecord {
    public ChangeCaptureEventEnablePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch Capture Event Ena";
    }
}
