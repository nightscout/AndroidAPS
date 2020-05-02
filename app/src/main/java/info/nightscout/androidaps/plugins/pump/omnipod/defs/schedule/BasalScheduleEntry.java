package info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class BasalScheduleEntry {
    private final double rate;
    private final Duration startTime;

    public BasalScheduleEntry(double rate, Duration startTime) {
        if (startTime.isLongerThan(Duration.standardHours(24).minus(Duration.standardSeconds(1))) || startTime.isShorterThan(Duration.ZERO) || startTime.getStandardSeconds() % 1800 != 0) {
            throw new IllegalArgumentException("Invalid start time");
        } else if (rate < 0D) {
            throw new IllegalArgumentException("Rate should be >= 0");
        } else if (rate > OmnipodConst.MAX_BASAL_RATE) {
            throw new IllegalArgumentException("Rate exceeds max basal rate");
        } else if (rate % OmnipodConst.POD_PULSE_SIZE > 0.000001 && rate % OmnipodConst.POD_PULSE_SIZE - OmnipodConst.POD_PULSE_SIZE < -0.000001) {
            throw new IllegalArgumentException("Unsupported basal rate precision");
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
                ", startTime=" + startTime.getStandardSeconds() + "s" +
                '}';
    }
}
