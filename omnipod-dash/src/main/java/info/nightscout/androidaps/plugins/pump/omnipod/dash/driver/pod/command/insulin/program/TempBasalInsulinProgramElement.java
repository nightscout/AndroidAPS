package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

public class TempBasalInsulinProgramElement extends BasalInsulinProgramElement {
    public TempBasalInsulinProgramElement(byte startSlotIndex, byte numberOfSlots, short totalTenthPulses) {
        super(startSlotIndex, numberOfSlots, totalTenthPulses);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        if (getTotalTenthPulses() == 0) {
            int i = ((int) ((((double) getDurationInSeconds()) * 1_000_000.0d) / ((double) getNumberOfSlots()))) | Integer.MIN_VALUE;
            buffer.putShort(getNumberOfSlots()) //
                    .putInt(i);
        } else {
            buffer.putShort(getTotalTenthPulses()) //
                    .putInt(getDelayBetweenTenthPulsesInUsec());
        }
        return buffer.array();
    }

}
