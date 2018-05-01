package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

/**
 * Created by geoff on 5/29/15.
 *
 * Just need a class to keep the pair together, for parcel transport.
 */
@Deprecated
public class TempBasalPair {
    private double mInsulinRate = 0.0;
    private int mDurationMinutes = 0;
    private boolean mIsPercent = false;

    public double getInsulinRate() {
        return mInsulinRate;
    }

    public void setInsulinRate(double insulinRate) {
        this.mInsulinRate = insulinRate;
    }

    public int getDurationMinutes() {
        return mDurationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.mDurationMinutes = durationMinutes;
    }

    public boolean isPercent() {
        return mIsPercent;
    }

    public void setIsPercent(boolean yesIsPercent) {
        this.mIsPercent = yesIsPercent;
    }

    public TempBasalPair() { }

    public TempBasalPair(double insulinRate, boolean isPercent, int durationMinutes) {
        mInsulinRate = insulinRate;
        mIsPercent = isPercent;
        mDurationMinutes = durationMinutes;
    }
}
