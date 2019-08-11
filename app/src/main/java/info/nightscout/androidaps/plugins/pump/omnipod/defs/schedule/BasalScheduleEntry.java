package info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule;

import org.joda.time.Duration;

import info.nightscout.androidaps.Constants;

public class BasalScheduleEntry {
    private final double rate;
    private final Duration startTime;

    public BasalScheduleEntry(double rate, Duration startTime) {
        if (rate < 0D) {
            throw new IllegalArgumentException("Rate should be >= 0");
        } else if (rate > Constants.MAX_BASAL_RATE) {
            throw new IllegalArgumentException("Rate exceeds max basal rate");
        }
        this.rate = rate;
        this.startTime = startTime;
    }

    public double getRate() {
        return rate;
    }

    public Duration getStartTime() {
        return startTime;
    }

    @Override
    public String toString() {
        return "BasalScheduleEntry{" +
                "rate=" + rate +
                ", startTime=" + startTime +
                '}';
    }
}
