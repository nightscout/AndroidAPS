package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.ProgramTempBasalCommand;

public class TempBasalInsulinProgramElement extends BasalInsulinProgramElement {
    private final ProgramTempBasalCommand.TempBasalMethod tempBasalMethod;

    public TempBasalInsulinProgramElement(byte startSlotIndex, byte numberOfSlots, short totalTenthPulses, int delayBetweenTenthPulsesInUsec, ProgramTempBasalCommand.TempBasalMethod tempBasalMethod) {
        super(startSlotIndex, numberOfSlots, totalTenthPulses, delayBetweenTenthPulsesInUsec);
        this.tempBasalMethod = tempBasalMethod;
    }

    @Override public byte[] getEncoded() {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        if (getTotalTenthPulses() == 0) {
            if (tempBasalMethod == ProgramTempBasalCommand.TempBasalMethod.FIRST_METHOD) {
                for (int i = 0; i < getNumberOfSlots(); i++) {
                    buffer.putShort((short) 0) //
                            .putInt((int) ((long) getDurationInSeconds() * 1_000_000d / getNumberOfSlots()));
                }
            } else {
                // Zero basal and temp basal second method
                buffer.putShort(getNumberOfSlots()) //
                        .putInt((int) ((long) getDurationInSeconds() * 1_000_000d / getNumberOfSlots()));
            }
        } else {
            buffer.putShort(getTotalTenthPulses()) //
                    .putInt(getDelayBetweenTenthPulsesInUsec());
        }
        return buffer.array();
    }

}
