package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public enum AlertSlot {
    EXPIRATION_IMMINENT((byte) 0x02),
    LOW_RESERVOIR((byte) 0x04),
    USER_POD_EXPIRATION((byte) 0x03),
    LUMP_OF_COAL_AND_EXPIRATION((byte) 0x07),
    UNKNOWN((byte) 255);

    private byte value;

    AlertSlot(byte value) {
        this.value = value;
    }

    public static AlertSlot byValue(byte value) {
        for (AlertSlot slot : values()) {
            if (slot.value == value) {
                return slot;
            }
        }
        return UNKNOWN;
    }

    public byte getValue() {
        return value;
    }
}
