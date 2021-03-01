package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

public class BasalShortInsulinProgramElement implements ShortInsulinProgramElement {
    private final byte numberOfSlots; // 4 bits
    private final short pulsesPerSlot; // 10 bits
    private final boolean extraAlternatePulse;

    public BasalShortInsulinProgramElement(byte numberOfSlots, short pulsesPerSlot, boolean extraAlternatePulse) {
        this.numberOfSlots = numberOfSlots;
        this.pulsesPerSlot = pulsesPerSlot;
        this.extraAlternatePulse = extraAlternatePulse;
    }

    @Override public byte[] getEncoded() {
        byte firstByte = (byte) ((((numberOfSlots - 1) & 0x0f) << 4) //
                | ((extraAlternatePulse ? 1 : 0) << 3) //
                | ((pulsesPerSlot >>> 8) & 0x03));

        return ByteBuffer.allocate(2) //
                .put(firstByte) //
                .put((byte) (pulsesPerSlot & 0xff)) //
                .array();
    }

    @Override public String toString() {
        return "ShortInsulinProgramElement{" +
                "numberOfSlotsMinusOne=" + numberOfSlots +
                ", pulsesPerSlot=" + pulsesPerSlot +
                ", extraAlternatePulse=" + extraAlternatePulse +
                '}';
    }
}
