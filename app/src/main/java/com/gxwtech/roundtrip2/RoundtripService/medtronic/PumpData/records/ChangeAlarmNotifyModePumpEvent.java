package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class ChangeAlarmNotifyModePumpEvent extends TimeStampedRecord {
    public ChangeAlarmNotifyModePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch Alarm Notify Mode";
    }
}
