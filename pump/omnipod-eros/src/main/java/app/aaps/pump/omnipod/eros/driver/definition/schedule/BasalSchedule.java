package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    @NonNull public List<BasalScheduleEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public BasalScheduleLookupResult lookup(@NonNull Duration offset) {
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

    @NonNull public List<BasalScheduleEntry> adjacentEqualRatesMergedEntries() {
        List<BasalScheduleEntry> mergedEntries = new ArrayList<>();
        Double lastRate = null;
        for (BasalScheduleEntry entry : entries) {
            if (lastRate == null || entry.getRate() != lastRate) {
                mergedEntries.add(entry);
            }
            lastRate = entry.getRate();
        }
        return mergedEntries;
    }

    public List<BasalScheduleDurationEntry> getDurations() {
        List<BasalScheduleDurationEntry> durations = new ArrayList<>();
        Duration last = Duration.standardHours(24);
        List<BasalScheduleEntry> basalScheduleEntries = reversedBasalScheduleEntries();
        for (BasalScheduleEntry entry : basalScheduleEntries) {
            durations.add(new BasalScheduleDurationEntry( //
                    entry.getRate(), //
                    entry.getStartTime(), //
                    last.minus(entry.getStartTime())));
            last = entry.getStartTime();
        }

        Collections.reverse(durations);
        return durations;
    }

    @NonNull @Override
    public String toString() {
        return "BasalSchedule{" +
                "entries=" + entries +
                '}';
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

    public static class BasalScheduleDurationEntry {
        private final double rate;
        private final Duration duration;
        private final Duration startTime;

        BasalScheduleDurationEntry(double rate, Duration startTime, Duration duration) {
            this.rate = rate;
            this.duration = duration;
            this.startTime = startTime;
        }

        public double getRate() {
            return rate;
        }

        public Duration getDuration() {
            return duration;
        }

        public Duration getStartTime() {
            return startTime;
        }
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
