package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeAlarmClockEnablePumpEvent extends TimeStampedRecord {
    public ChangeAlarmClockEnablePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Alarm Clock Enable";
    }
}
