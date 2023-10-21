package com.microtechmd.equil.driver.definition;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.Objects;


public class BasalScheduleEntry {
    private final double rate;
    private final Duration startTime;

    public BasalScheduleEntry(double rate, Duration startTime) {
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

    @Override public boolean equals(Object o) {
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
