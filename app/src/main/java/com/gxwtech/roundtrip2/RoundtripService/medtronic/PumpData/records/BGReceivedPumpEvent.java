package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.util.ByteUtil;

public class BGReceivedPumpEvent extends TimeStampedRecord {
    private int amount = 0;
    private byte[] meter = new byte[3];

    public BGReceivedPumpEvent() {
    }

    @Override
    public int getLength() { return 10; }

    @Override
    public String getShortTypeName() {
        return "BG Received";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!super.simpleParse(data,2)) {
            return false;
        }
        amount = (asUINT8(data[1]) << 3) + (asUINT8(data[4])>>5);
        meter = ByteUtil.substring(data,7,3);
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        amount = in.getInt("amount");
        meter = in.getByteArray("meter");
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putInt("amount",amount);
        in.putByteArray("meter",meter);
    }
}
