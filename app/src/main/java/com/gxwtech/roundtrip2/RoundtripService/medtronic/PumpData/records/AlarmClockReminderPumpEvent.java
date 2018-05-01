package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/11/16.
 */
public class AlarmClockReminderPumpEvent extends TimeStampedRecord {
    public AlarmClockReminderPumpEvent() {};

    @Override
    public String getShortTypeName() {
        return "Alarm Reminder";
    }
}
