package app.aaps.pump.omnipod.eros.driver.definition;

public enum BeepRepeat {
    ONCE((byte) 0x00),
    EVERY_MINUTE_FOR_3_MINUTES_REPEAT_EVERY_60_MINUTES((byte) 0x01),
    EVERY_MINUTE_FOR_15_MINUTES((byte) 0x02),
    EVERY_MINUTE_FOR_3_MINUTES_REPEAT_EVERY_15_MINUTES((byte) 0x03),
    EVERY_3_MINUTES_DELAYED((byte) 0x04),
    EVERY_60_MINUTES((byte) 0x05),
    EVERY_15_MINUTES((byte) 0x06),
    EVERY_15_MINUTES_DELAYED((byte) 0x07),
    EVERY_5_MINUTES((byte) 0x08);

    private final byte value;

    BeepRepeat(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
