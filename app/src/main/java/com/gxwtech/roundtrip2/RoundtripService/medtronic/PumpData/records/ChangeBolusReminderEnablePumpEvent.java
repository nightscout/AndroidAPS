package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeBolusReminderEnablePumpEvent extends TimeStampedRecord {
    public ChangeBolusReminderEnablePumpEvent() {}

    @Override
    public int getLength() { return 9; }

    @Override
    public String getShortTypeName() {
        return "Ch Bolus Rmndr Enable";
    }

}
