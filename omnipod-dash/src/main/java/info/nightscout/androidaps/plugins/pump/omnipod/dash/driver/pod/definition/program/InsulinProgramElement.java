package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.program;

import java.nio.ByteBuffer;

public class InsulinProgramElement {
    private final byte numberOfHalfOurEntries; // 4 bits
    private final short numberOfPulsesPerHalfOurEntry; // 10 bits
    private final boolean extraAlternatePulse;

    public InsulinProgramElement(byte numberOfHalfOurEntries, short numberOfPulsesPerHalfOurEntry, boolean extraAlternatePulse) {
        this.numberOfHalfOurEntries = numberOfHalfOurEntries;
        this.numberOfPulsesPerHalfOurEntry = numberOfPulsesPerHalfOurEntry;
        this.extraAlternatePulse = extraAlternatePulse;
    }

    public byte[] getEncoded() {
        byte firstByte = (byte) ((((numberOfHalfOurEntries - 1) & 0x0f) << 4)
                | ((extraAlternatePulse ? 1 : 0) << 3)
                | ((numberOfPulsesPerHalfOurEntry >>> 8) & 0x03));

        return ByteBuffer.allocate(2) //
                .put(firstByte)
                .put((byte) (numberOfPulsesPerHalfOurEntry & 0xff)) //
                .array();
    }
}
