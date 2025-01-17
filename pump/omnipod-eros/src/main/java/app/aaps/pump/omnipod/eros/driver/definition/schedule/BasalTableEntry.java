package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import androidx.annotation.NonNull;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.IRawRepresentable;

public class BasalTableEntry implements IRawRepresentable {

    private final int segments;
    private final int pulses;
    private final boolean alternateSegmentPulse;

    public BasalTableEntry(int segments, int pulses, boolean alternateSegmentPulse) {
        this.segments = segments;
        this.pulses = pulses;
        this.alternateSegmentPulse = alternateSegmentPulse;
    }

    @Override
    public byte[] getRawData() {
        byte[] rawData = new byte[2];
        byte pulsesHighByte = (byte) ((pulses >>> 8) & 0b11);
        byte pulsesLowByte = (byte) pulses;
        rawData[0] = (byte) ((byte) ((segments - 1) << 4) + (byte) ((alternateSegmentPulse ? 1 : 0) << 3) + pulsesHighByte);
        rawData[1] = pulsesLowByte;
        return rawData;
    }

    public int getChecksum() {
        int checksumPerSegment = ByteUtil.INSTANCE.convertUnsignedByteToInt((byte) pulses) + (pulses >>> 8);
        return (checksumPerSegment * segments + (alternateSegmentPulse ? segments / 2 : 0));
    }

    public int getSegments() {
        return this.segments;
    }

    public int getPulses() {
        return pulses;
    }

    public boolean isAlternateSegmentPulse() {
        return alternateSegmentPulse;
    }

    @NonNull @Override
    public String toString() {
        return "BasalTableEntry{" +
                "segments=" + segments +
                ", pulses=" + pulses +
                ", alternateSegmentPulse=" + alternateSegmentPulse +
                '}';
    }
}
