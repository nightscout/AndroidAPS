package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BasalProgram {
    private final List<Segment> segments;

    public BasalProgram(List<Segment> segments) {
        if (segments == null) {
            throw new IllegalArgumentException("segments can not be null");
        }

        // TODO validate segments

        this.segments = new ArrayList<>(segments);
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
    }

    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public boolean isZeroBasal() {
        int total = 0;
        for (Segment segment : segments) {
            total += segment.getBasalRateInHundredthUnitsPerHour();
        }
        return total == 0;
    }

    public boolean hasZeroUnitSegments() {
        for (Segment segment : segments) {
            if (segment.getBasalRateInHundredthUnitsPerHour() == 0) {
                return true;
            }
        }
        return false;
    }

    public static class Segment {
        private static final byte PULSES_PER_UNIT = 20;

        private final short startSlotIndex;
        private final short endSlotIndex;
        private final int basalRateInHundredthUnitsPerHour;

        public Segment(short startSlotIndex, short endSlotIndex, int basalRateInHundredthUnitsPerHour) {
            this.startSlotIndex = startSlotIndex;
            this.endSlotIndex = endSlotIndex;
            this.basalRateInHundredthUnitsPerHour = basalRateInHundredthUnitsPerHour;
        }

        public short getStartSlotIndex() {
            return startSlotIndex;
        }

        public short getEndSlotIndex() {
            return endSlotIndex;
        }

        public int getBasalRateInHundredthUnitsPerHour() {
            return basalRateInHundredthUnitsPerHour;
        }

        public short getPulsesPerHour() {
            return (short) (basalRateInHundredthUnitsPerHour * PULSES_PER_UNIT / 100);
        }

        public short getNumberOfSlots() {
            return (short) (endSlotIndex - startSlotIndex);
        }

        @Override public String toString() {
            return "Segment{" +
                    "startSlotIndex=" + startSlotIndex +
                    ", endSlotIndex=" + endSlotIndex +
                    ", basalRateInHundredthUnitsPerHour=" + basalRateInHundredthUnitsPerHour +
                    '}';
        }
    }

    @Override public String toString() {
        return "BasalProgram{" +
                "segments=" + segments +
                '}';
    }
}
