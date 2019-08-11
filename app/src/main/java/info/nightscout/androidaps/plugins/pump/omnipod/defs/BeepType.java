package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum BeepType {
    NO_BEEP((byte) 0x00),
    BEEP_BEEP_BEEP_BEEP((byte) 0x01),
    BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP((byte) 0x02),
    BIP_BIP((byte) 0x03),
    BEEP((byte) 0x04),
    BEEP_BEEP_BEEP((byte) 0x05),
    BEEEEEEP((byte) 0x06),
    BIP_BIP_BIP_BIP_BIP_BIP((byte) 0x07),
    BEEEP_BEEEP((byte) 0x08),
    BEEP_BEEP((byte) 0xB),
    BEEEP((byte) 0xC),
    BIP_BEEEEEP((byte) 0xD),
    FIVE_SECONDS_BEEP((byte) 0xE),
    BEEP_CONFIG_NO_BEEP((byte) 0xF);

    private byte value;

    BeepType(byte value) {
        this.value = value;
    }

    public static BeepType fromByte(byte value) {
        for (BeepType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown BeepType: " + value);
    }

    public byte getValue() {
        return value;
    }

}
