package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 7/16/16.
 */
public class InsulinMarkerEvent extends TimeStampedRecord {
    public InsulinMarkerEvent() {}

    @Override
    public int getLength() {
        return 8;
    }

    /*
     Darrell Wright:
     it is a manual entry of a bolus that the pump didn't deliver, so opcode, timestamp and at least a number to represent the units of insulin
    */

    @Override
    public String getShortTypeName() {
        return "UnknownInsulin";
    }
}
