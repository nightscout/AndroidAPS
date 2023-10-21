package com.microtechmd.equil.driver.definition;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import app.aaps.core.interfaces.profile.Profile;


public class BasalSchedule {
    private final List<BasalScheduleEntry> entries;

    public BasalSchedule(List<BasalScheduleEntry> entries) {
        if (entries == null || entries.size() == 0) {
            throw new IllegalArgumentException("Entries can not be empty");
        } else if (!entries.get(0).getStartTime().isEqual(Duration.ZERO)) {
            throw new IllegalArgumentException("First basal schedule entry should have 0 offset");
        }
        this.entries = new ArrayList<>(entries);
    }

    public double rateAt(Duration offset) {
        return lookup(offset).getBasalScheduleEntry().getRate();
    }

    public static BasalSchedule mapProfileToBasalSchedule(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile can not be null");
        }
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        if (basalValues == null) {
            throw new IllegalArgumentException("Basal values can not be null");
        }
        List<BasalScheduleEntry> entries = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double value = profile.getBasalTimeFromMidnight(i * 60 * 60);
            BasalScheduleEntry basalScheduleEntry = new BasalScheduleEntry(value,
                    Duration.standardSeconds(i * 60 * 60));
            entries.add(basalScheduleEntry);
        }
        return new BasalSchedule(entries);
    }

    public List<BasalScheduleEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public BasalScheduleLookupResult lookup(Duration offset) {
        if (offset.isLongerThan(Duration.standardHours(24)) || offset.isShorterThan(Duration.ZERO)) {
            throw new IllegalArgumentException("Invalid duration");
        }

        List<BasalScheduleEntry> reversedBasalScheduleEntries = reversedBasalScheduleEntries();

        Duration last = Duration.standardHours(24);
        int index = 0;
        for (BasalScheduleEntry entry : reversedBasalScheduleEntries) {
            if (entry.getStartTime().isShorterThan(offset) || entry.getStartTime().equals(offset)) {
                return new BasalScheduleLookupResult( //
                        reversedBasalScheduleEntries.size() - (index + 1), //
                        entry, //
                        entry.getStartTime(), //
                        last.minus(entry.getStartTime()));
            }
            last = entry.getStartTime();
            index++;
        }

        throw new IllegalArgumentException("Basal schedule incomplete");
    }

    private List<BasalScheduleEntry> reversedBasalScheduleEntries() {
        List<BasalScheduleEntry> reversedEntries = new ArrayList<>(entries);
        Collections.reverse(reversedEntries);
        return reversedEntries;
    }


    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasalSchedule that = (BasalSchedule) o;
        return entries.equals(that.entries);
    }

    @Override public int hashCode() {
        return Objects.hash(entries);
    }


    public static class BasalScheduleLookupResult {
        private final int index;
        private final BasalScheduleEntry basalScheduleEntry;
        private final Duration startTime;
        private final Duration duration;

        BasalScheduleLookupResult(int index, BasalScheduleEntry basalScheduleEntry, Duration startTime, Duration duration) {
            this.index = index;
            this.basalScheduleEntry = basalScheduleEntry;
            this.startTime = startTime;
            this.duration = duration;
        }

        public int getIndex() {
            return index;
        }

        BasalScheduleEntry getBasalScheduleEntry() {
            return basalScheduleEntry;
        }

        public Duration getStartTime() {
            return startTime;
        }

        public Duration getDuration() {
            return duration;
        }
    }
}
