package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBGReminderEnablePumpEvent extends TimeStampedRecord {
    public ChangeBGReminderEnablePumpEvent() {}

    @Override
    public String getShortTypeName() {
        return "Ch BG Rmndr Enable";
    }
}
