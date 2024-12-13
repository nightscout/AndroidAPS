package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joda.time.Duration;

import java.util.Objects;

import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;

public class BasalScheduleEntry {
    private final double rate;
    private final Duration startTime;

    public BasalScheduleEntry(double rate, Duration startTime) {
        if (startTime.isLongerThan(Duration.standardHours(24).minus(Duration.standardSeconds(1))) || startTime.isShorterThan(Duration.ZERO) || startTime.getStandardSeconds() % 1800 != 0) {
            throw new IllegalArgumentException("Invalid start time");
        } else if (rate < 0D) {
            throw new IllegalArgumentException("Rate should be >= 0");
        } else if (rate > OmnipodConstants.MAX_BASAL_RATE) {
            throw new IllegalArgumentException("Rate exceeds max basal rate");
        } else if (rate % OmnipodConstants.POD_PULSE_SIZE > 0.000001 && rate % OmnipodConstants.POD_PULSE_SIZE - OmnipodConstants.POD_PULSE_SIZE < -0.000001) {
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

    @NonNull @Override
    public String toString() {
        return "BasalScheduleEntry{" +
                "rate=" + rate +
                ", startTime=" + startTime.getStandardSeconds() + "s" +
                '}';
    }

    @Override public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasalScheduleEntry that = (BasalScheduleEntry) o;
        return Double.compare(that.rate, rate) == 0 &&
                Objects.equals(startTime, that.startTime);
    }

    @Override public int hashCode() {
        return Objects.hash(rate, startTime);
    }
}
