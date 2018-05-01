package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

public class Model522ResultTotalsPumpEvent extends TimeStampedRecord {
    public Model522ResultTotalsPumpEvent() {}

    public int getDatestampOffset() { return 1; }

    @Override
    public int getLength() { return 44; }

    @Override
    public String getShortTypeName() {
        return "M522 Result Totals";
    }
}
