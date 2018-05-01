package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class CalBgForPhPumpEvent extends TimeStampedRecord {
    private int amount = 0;

    public CalBgForPhPumpEvent() {
    }

    @Override
    public String getShortTypeName() {
        return "Cal Bg For Ph";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data,2)) {
            return false;
        }
        amount = ((asUINT8(data[6]) & 0x80) << 1) + asUINT8(data[1]);
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        amount = in.getInt("amount",0);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putInt("amount",amount);
    }

}
