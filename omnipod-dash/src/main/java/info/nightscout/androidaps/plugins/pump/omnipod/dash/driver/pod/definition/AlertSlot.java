package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public enum AlertSlot {
    AUTO_OFF((byte) 0x00),
    MULTI_COMMAND((byte) 0x01),
    EXPIRATION_IMMINENT((byte) 0x02),
    USER_SET_EXPIRATION((byte) 0x03),
    LOW_RESERVOIR((byte) 0x04),
    SUSPEND_IN_PROGRESS((byte) 0x05),
    SUSPEND_ENDED((byte) 0x06),
    EXPIRATION((byte) 0x07),
    UNKNOWN((byte) 0xff);

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
