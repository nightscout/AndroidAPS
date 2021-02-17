package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

public class BasalInsulinProgramElement implements Encodable {
    private final byte startSlotIndex;
    private final byte numberOfSlots;
    private final short totalTenthPulses;
    private final int delayBetweenTenthPulsesInUsec;

    public BasalInsulinProgramElement(byte startSlotIndex, byte numberOfSlots, short totalTenthPulses, int delayBetweenTenthPulsesInUsec) {
        this.startSlotIndex = startSlotIndex;
        this.numberOfSlots = numberOfSlots;
        this.totalTenthPulses = totalTenthPulses;
        this.delayBetweenTenthPulsesInUsec = delayBetweenTenthPulsesInUsec;
    }

    @Override public byte[] getEncoded() {
        return ByteBuffer.allocate(6) //
                .putShort(totalTenthPulses) //
                .putInt(delayBetweenTenthPulsesInUsec) //
                .array();
    }

    public byte getStartSlotIndex() {
        return startSlotIndex;
    }

    public byte getNumberOfSlots() {
        return numberOfSlots;
    }

    public short getDurationInSeconds() {
        return (short) (numberOfSlots * 1_800);
    }

    public short getTotalTenthPulses() {
        return totalTenthPulses;
    }

    public int getDelayBetweenTenthPulsesInUsec() {
        return delayBetweenTenthPulsesInUsec;
    }

    @Override public String toString() {
        return "LongInsulinProgramElement{" +
                "startSlotIndex=" + startSlotIndex +
                ", numberOfSlots=" + numberOfSlots +
                ", totalTenthPulses=" + totalTenthPulses +
                ", delayBetweenTenthPulsesInUsec=" + delayBetweenTenthPulsesInUsec +
                '}';
    }
}
