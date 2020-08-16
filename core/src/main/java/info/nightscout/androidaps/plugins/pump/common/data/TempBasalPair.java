package info.nightscout.androidaps.plugins.pump.common.data;

import com.google.gson.annotations.Expose;

public class TempBasalPair {

    @Expose
    protected double insulinRate = 0.0d;
    @Expose
    protected int durationMinutes = 0;
    @Expose
    protected boolean isPercent = false;

    private Long start;
    private Long end;

    public TempBasalPair() {
    }


    public TempBasalPair(double insulinRate, boolean isPercent, int durationMinutes) {
        this.insulinRate = insulinRate;
        this.isPercent = isPercent;
        this.durationMinutes = durationMinutes;
    }


    public double getInsulinRate() {
        return insulinRate;
    }


    public void setInsulinRate(double insulinRate) {
        this.insulinRate = insulinRate;
    }


    public int getDurationMinutes() {
        return durationMinutes;
    }


    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }


    public boolean isPercent() {
        return isPercent;
    }


    public void setIsPercent(boolean yesIsPercent) {
        this.isPercent = yesIsPercent;
    }

    public void setStartTime(Long startTime) {
        this.start = startTime;
    }


    public void setEndTime(Long endTime) {
        this.end = endTime;
    }


    @Override
    public String toString() {
        return "TempBasalPair [" + "Rate=" + insulinRate + ", DurationMinutes=" + durationMinutes + ", IsPercent="
                + isPercent + "]";
    }
}
