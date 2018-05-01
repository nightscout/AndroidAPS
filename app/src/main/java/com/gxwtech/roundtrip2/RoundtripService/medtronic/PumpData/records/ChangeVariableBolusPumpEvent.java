package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

/**
 * Created by geoff on 6/5/16.
 */
public class ChangeVariableBolusPumpEvent extends TimeStampedRecord {
    public ChangeVariableBolusPumpEvent(){}

    @Override
    public String getShortTypeName() {
        return "Ch Var. Bolus";
    }
}
