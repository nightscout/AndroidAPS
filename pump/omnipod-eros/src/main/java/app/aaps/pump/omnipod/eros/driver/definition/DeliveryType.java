package app.aaps.pump.omnipod.eros.driver.definition;

public enum DeliveryType {
    NONE((byte) 0x00),
    BASAL((byte) 0x01),
    TEMP_BASAL((byte) 0x02),
    BOLUS((byte) 0x04);

    private final byte value;

    DeliveryType(byte value) {
        this.value = value;
    }

    public static DeliveryType fromByte(byte value) {
        for (DeliveryType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DeliveryType: " + value);
    }

    public byte getValue() {
        return value;
    }
}
