package app.aaps.pump.omnipod.eros.driver.definition;

public enum AlertSlot {
    SLOT0((byte) 0x00),
    SLOT1((byte) 0x01),
    SLOT2((byte) 0x02),
    SLOT3((byte) 0x03),
    SLOT4((byte) 0x04),
    SLOT5((byte) 0x05),
    SLOT6((byte) 0x06),
    SLOT7((byte) 0x07);

    private final byte value;

    AlertSlot(byte value) {
        this.value = value;
    }

    public static AlertSlot fromByte(byte value) {
        for (AlertSlot type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AlertSlot: " + value);
    }

    public byte getBitMaskValue() {
        return (byte) (1 << value);
    }

    public byte getValue() {
        return value;
    }
}
