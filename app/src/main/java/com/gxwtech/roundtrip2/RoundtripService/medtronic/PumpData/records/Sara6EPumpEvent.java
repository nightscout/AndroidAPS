package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpTimeStamp;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.TimeFormat;

public class Sara6EPumpEvent extends TimeStampedRecord {
    public Sara6EPumpEvent() {
    }

    @Override
    public int getLength() {
        return 52;
    }

    @Override
    public String getShortTypeName() {
        return "Sara6E";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        // We don't understand this event...
        // Minimum 16 characters? date components?
        if (16 > data.length) {
            return false;
        }
        try {
            timestamp = new PumpTimeStamp(TimeFormat.parse2ByteDate(data,1));
        } catch (org.joda.time.IllegalFieldValueException e) {
            return false;
        }
        return true;
    }

}
