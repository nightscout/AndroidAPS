package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;

public class BasalDeliveryTable {

    public static final int SEGMENT_DURATION = 30 * 60;
    public static final int MAX_PULSES_PER_RATE_ENTRY = 6400;

    private static final int NUM_SEGMENTS = 48;
    private static final int MAX_SEGMENTS_PER_ENTRY = 16;

    private final List<BasalTableEntry> entries = new ArrayList<>();

    public BasalDeliveryTable(BasalSchedule schedule) {
        TempSegment[] expandedSegments = new TempSegment[48];

        boolean halfPulseRemainder = false;
        for (int i = 0; i < NUM_SEGMENTS; i++) {
            double rate = schedule.rateAt(Duration.standardMinutes(i * 30));
            int pulsesPerHour = (int) Math.round(rate / OmnipodConstants.POD_PULSE_SIZE);
            int pulsesPerSegment = pulsesPerHour >>> 1;
            boolean halfPulse = (pulsesPerHour & 0b1) != 0;

            expandedSegments[i] = new TempSegment(pulsesPerSegment + (halfPulseRemainder && halfPulse ? 1 : 0));
            halfPulseRemainder = halfPulseRemainder != halfPulse;
        }

        List<TempSegment> segmentsToMerge = new ArrayList<>();

        boolean altSegmentPulse = false;
        for (TempSegment segment : expandedSegments) {
            if (segmentsToMerge.isEmpty()) {
                segmentsToMerge.add(segment);
                continue;
            }

            TempSegment firstSegment = segmentsToMerge.get(0);

            int delta = segment.getPulses() - firstSegment.getPulses();
            if (segmentsToMerge.size() == 1) {
                altSegmentPulse = delta == 1;
            }

            int expectedDelta = altSegmentPulse ? segmentsToMerge.size() % 2 : 0;

            if (expectedDelta != delta || segmentsToMerge.size() == MAX_SEGMENTS_PER_ENTRY) {
                addBasalTableEntry(segmentsToMerge, altSegmentPulse);
                segmentsToMerge.clear();
            }

            segmentsToMerge.add(segment);
        }

        addBasalTableEntry(segmentsToMerge, altSegmentPulse);
    }

    public BasalDeliveryTable(double tempBasalRate, Duration duration) {
        int pulsesPerHour = (int) Math.round(tempBasalRate / OmnipodConstants.POD_PULSE_SIZE);
        int pulsesPerSegment = pulsesPerHour >> 1;
        boolean alternateSegmentPulse = (pulsesPerHour & 0b1) != 0;

        int remaining = (int) Math.round(duration.getStandardSeconds() / (double) BasalDeliveryTable.SEGMENT_DURATION);

        while (remaining > 0) {
            int segments = Math.min(MAX_SEGMENTS_PER_ENTRY, remaining);
            entries.add(new BasalTableEntry(segments, pulsesPerSegment, segments > 1 && alternateSegmentPulse));
            remaining -= segments;
        }
    }

    private void addBasalTableEntry(List<TempSegment> segments, boolean alternateSegmentPulse) {
        entries.add(new BasalTableEntry(segments.size(), segments.get(0).getPulses(), alternateSegmentPulse));
    }

    public BasalTableEntry[] getEntries() {
        return entries.toArray(new BasalTableEntry[0]);
    }

    byte numSegments() {
        byte numSegments = 0;
        for (BasalTableEntry entry : entries) {
            numSegments += entry.getSegments();
        }
        return numSegments;
    }

    @NonNull @Override
    public String toString() {
        return "BasalDeliveryTable{" +
                "entries=" + entries +
                '}';
    }

    private static class TempSegment {
        private final int pulses;

        TempSegment(int pulses) {
            this.pulses = pulses;
        }

        int getPulses() {
            return pulses;
        }
    }
}

