package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeReservoirWarningTimePumpEvent extends TimeStampedRecord {
    public ChangeReservoirWarningTimePumpEvent(){}

    @Override
    public String getShortTypeName() {
        return "Ch Res Warn Time";
    }
}
