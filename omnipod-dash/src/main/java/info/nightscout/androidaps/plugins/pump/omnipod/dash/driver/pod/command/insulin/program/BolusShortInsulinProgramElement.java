package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

public class BolusShortInsulinProgramElement implements ShortInsulinProgramElement {
    private short numberOfPulses;

    public BolusShortInsulinProgramElement(short numberOfPulses) {
        this.numberOfPulses = numberOfPulses;
    }

    public short getNumberOfPulses() {
        return numberOfPulses;
    }

    @Override public byte[] getEncoded() {
        return ByteBuffer.allocate(2).putShort(numberOfPulses).array();
    }
}
