package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;


import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;

public class TempBasalRatePumpEvent extends TimeStampedRecord {
    private double basalRate = 0.0; // rate in Units/hr
    private boolean mIsPercent = false; // The value is either an absolute number or a percentage

    public TempBasalRatePumpEvent() { }

    @Override
    public int getLength() { return 8; }

    @Override
    public String getShortTypeName() {
        return "Temp Basal Rate";
    }

    public double getBasalRate() { return basalRate; }
    public boolean isPercent() { return mIsPercent; }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        if (!simpleParse(data,2)) {
            return false;
        }
        if ((asUINT8(data[7])>>3)==0) {
            mIsPercent = false;
            basalRate = (double)(asUINT8(data[1])) / 40.0;
        } else {
            mIsPercent = true;
            basalRate = asUINT8(data[1]);
        }
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        basalRate = in.getDouble("basalRate",0);
        mIsPercent = in.getBoolean("mIsPercent",false);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        in.putDouble("basalRate",basalRate);
        in.putBoolean("mIsPercent",mIsPercent);
        super.writeToBundle(in);
    }

}
