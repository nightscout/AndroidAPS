package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class BasalProfileStart extends TimeStampedRecord {
    private static final String TAG = "BasalProfileStart";
    private int offset = 0;
    private double rate = 0.0;
    private int profileIndex = 0;

    public BasalProfileStart() { }

    @Override
    public int getLength() { return 10; }

    @Override
    public String getShortTypeName() {
        return "Basal Profile Start";
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data,2)) {
            return false;
        }

        profileIndex = asUINT8(data[1]);
        offset = asUINT8(data[7]) * 30 * 1000 * 60;
        rate = (double)(asUINT8(data[8])) / 40.0;
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        offset = in.getInt("offset");
        rate = in.getDouble("rate");
        profileIndex = in.getInt("profileIndex");
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putInt("offset",offset);
        in.putDouble("rate",rate);
        in.putInt("profileIndex",profileIndex);
    }

}
