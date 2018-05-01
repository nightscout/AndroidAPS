package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpTimeStamp;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.TimeFormat;

public class ResultDailyTotalPumpEvent extends TimeStampedRecord {
    private static final String TAG = "ResultDailyTotalPumpEvent";
    public ResultDailyTotalPumpEvent() {
    }

    @Override
    public int getDatestampOffset() { return 5; }

    @Override
    public int getLength() { return PumpModel.isLargerFormat(model) ? 10 : 7; }

    @Override
    protected boolean collectTimeStamp(byte[] data, int offset) {
        try {
            // This might be a 5 byte date on largerFormat
            timestamp = new PumpTimeStamp(TimeFormat.parse2ByteDate(data, offset));
        } catch (org.joda.time.IllegalFieldValueException e) {
            return false;
        }
        return true;
    }

    @Override
    public String getShortTypeName() {
        return "Result Daily Total";
    }

}
