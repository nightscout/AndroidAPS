package app.aaps.pump.omnipod.eros.driver.definition;


// BeepConfigType is used only for the $1E Beep Config Command.
public enum BeepConfigType {
    // 0x0 always returns an error response for Beep Config (use 0xF for no beep)
    BEEP_BEEP_BEEP_BEEP((byte) 0x01),
    BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP((byte) 0x02),
    BIP_BIP((byte) 0x03),
    BEEP((byte) 0x04),
    BEEP_BEEP_BEEP((byte) 0x05),
    BEEEEEEP((byte) 0x06),
    BIP_BIP_BIP_BIP_BIP_BIP((byte) 0x07),
    BEEEP_BEEEP((byte) 0x08),
    // 0x9 and 0xA always return an error response for Beep Config
    BEEP_BEEP((byte) 0xB),
    BEEEP((byte) 0xC),
    BIP_BEEEEEP((byte) 0xD),
    FIVE_SECONDS_BEEP((byte) 0xE), // can only be used if Pod is currently suspended
    NO_BEEP((byte) 0xF);

    private final byte value;

    BeepConfigType(byte value) {
        this.value = value;
    }

    public static BeepConfigType fromByte(byte value) {
        for (BeepConfigType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown BeepConfigType: " + value);
    }

    public byte getValue() {
        return value;
    }

}
