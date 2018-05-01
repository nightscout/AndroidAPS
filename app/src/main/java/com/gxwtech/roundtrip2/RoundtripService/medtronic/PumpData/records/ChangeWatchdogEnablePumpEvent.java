package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeWatchdogEnablePumpEvent extends TimeStampedRecord {
    public ChangeWatchdogEnablePumpEvent(){}

    @Override
    public String getShortTypeName() {
        return "Ch Watchdog Enable";
    }
}
