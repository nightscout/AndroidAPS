package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import static app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants.BASAL_STEP_DURATION;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.IRawRepresentable;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;

public class RateEntry implements IRawRepresentable {

    private final double totalPulses;
    // We use a double for the delay between pulses because the Joda time API lacks precision for our calculations
    private final double delayBetweenPulsesInSeconds;

    private RateEntry(double totalPulses, double delayBetweenPulsesInSeconds) {
        this.totalPulses = totalPulses;
        this.delayBetweenPulsesInSeconds = delayBetweenPulsesInSeconds;
    }

    @NonNull public static List<RateEntry> createEntries(double rate, Duration duration) {
        if (Duration.ZERO.equals(duration)) {
            throw new IllegalArgumentException("Duration may not be 0 minutes.");
        }
        if (duration.getStandardMinutes() % BASAL_STEP_DURATION.getStandardMinutes() != 0) {
            throw new IllegalArgumentException("Duration must be a multiple of " + BASAL_STEP_DURATION.getStandardMinutes() + " minutes.");
        }

        List<RateEntry> entries = new ArrayList<>();
        int remainingSegments = (int) Math.round(duration.getStandardSeconds() / 1800.0);
        double pulsesPerSegment = (int) Math.round(rate / OmnipodConstants.POD_PULSE_SIZE) / 2.0;
        int maxSegmentsPerEntry = pulsesPerSegment > 0 ? (int) (BasalDeliveryTable.MAX_PULSES_PER_RATE_ENTRY / pulsesPerSegment) : 1;

        double durationInHours = duration.getStandardSeconds() / 3600.0;

        double remainingPulses = rate * durationInHours / OmnipodConstants.POD_PULSE_SIZE;
        double delayBetweenPulses = 3600 / rate * OmnipodConstants.POD_PULSE_SIZE;

        while (remainingSegments > 0) {
            if (rate == 0.0) {
                entries.add(new RateEntry(0, 30D * 60));
                remainingSegments -= 1;
            } else {
                int numSegments = Math.min(maxSegmentsPerEntry, (int) Math.round(remainingPulses / pulsesPerSegment));
                double totalPulses = pulsesPerSegment * numSegments;
                entries.add(new RateEntry(totalPulses, delayBetweenPulses));
                remainingSegments -= numSegments;
                remainingPulses -= totalPulses;
            }
        }

        return entries;
    }

    public double getTotalPulses() {
        return totalPulses;
    }

    public double getDelayBetweenPulsesInSeconds() {
        return delayBetweenPulsesInSeconds;
    }

    @Override
    public byte[] getRawData() {
        byte[] rawData = new byte[0];
        rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt16((int) Math.round(totalPulses * 10)));
        if (totalPulses == 0) {
            rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt((int) (delayBetweenPulsesInSeconds * 1000 * 1000)));
        } else {
            rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt((int) (delayBetweenPulsesInSeconds * 1000 * 100)));
        }
        return rawData;
    }

    @NonNull @Override
    public String toString() {
        return "RateEntry{" +
                "totalPulses=" + totalPulses +
                ", delayBetweenPulsesInSeconds=" + delayBetweenPulsesInSeconds +
                '}';
    }
}
