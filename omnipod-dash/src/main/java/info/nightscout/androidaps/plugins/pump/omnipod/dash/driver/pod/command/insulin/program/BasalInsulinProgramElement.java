package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

import static info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramBasalUtil.MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT;

public class BasalInsulinProgramElement implements Encodable {
    private final byte startSlotIndex;
    private final byte numberOfSlots;
    private final short totalTenthPulses;

    public BasalInsulinProgramElement(byte startSlotIndex, byte numberOfSlots, short totalTenthPulses) {
        this.startSlotIndex = startSlotIndex;
        this.numberOfSlots = numberOfSlots;
        this.totalTenthPulses = totalTenthPulses;
    }

    @Override public byte[] getEncoded() {
        return ByteBuffer.allocate(6) //
                .putShort(totalTenthPulses) //
                .putInt(totalTenthPulses == 0 ? Integer.MIN_VALUE | getDelayBetweenTenthPulsesInUsec() : getDelayBetweenTenthPulsesInUsec()) //
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
        if (totalTenthPulses == 0) {
            return MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT;
        }
        return (int) (((long) MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT * numberOfSlots) / (double) totalTenthPulses);
    }

    @Override public String toString() {
        return "LongInsulinProgramElement{" +
                "startSlotIndex=" + startSlotIndex +
                ", numberOfSlots=" + numberOfSlots +
                ", totalTenthPulses=" + totalTenthPulses +
                ", delayBetweenTenthPulsesInUsec=" + getDelayBetweenTenthPulsesInUsec() +
                '}';
    }
}
