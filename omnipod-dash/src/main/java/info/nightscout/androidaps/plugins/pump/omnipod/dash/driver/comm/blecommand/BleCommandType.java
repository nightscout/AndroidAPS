package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.blecommand;

public enum BleCommandType {
    RTS((byte) 0x00),
    CTS((byte) 0x01),
    NACK((byte) 0x02),
    ABORT((byte) 0x03),
    SUCCESS((byte) 0x04),
    FAIL((byte) 0x05),
    HELLO((byte) 0x06);

    public final byte value;

    BleCommandType(byte value) {
        this.value = value;
    }

    public static BleCommandType byValue(byte value) {
        for (BleCommandType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown BleCommandType: " + value);
    }

    public byte getValue() {
        return this.value;
    }
}
