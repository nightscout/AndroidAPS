package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

// This event existed as 0x18 in Roundtrip and early Decocare,
// but I don't see a corresponding event in RileyLink_ios.
public class NewTimeSet extends TimeStampedRecord {
    public NewTimeSet() {
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        return false;
    }
}
