package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base;

public enum CommandType {
    SET_UNIQUE_ID((byte) 0x03),
    GET_VERSION((byte) 0x07),
    GET_STATUS((byte) 0x0e),
    SILENCE_ALERTS((byte) 0x11),
    PROGRAM_BASAL((byte) 0x13), // Always preceded by 0x1a
    PROGRAM_TEMP_BASAL((byte) 0x16), // Always preceded by 0x1a
    PROGRAM_BOLUS((byte) 0x17), // Always preceded by 0x1a
    PROGRAM_ALERTS((byte) 0x19),
    PROGRAM_INSULIN((byte) 0x1a), // Always followed by one of: 0x13, 0x16, 0x17
    DEACTIVATE((byte) 0x1c),
    PROGRAM_BEEPS((byte) 0x1e),
    STOP_DELIVERY((byte) 0x1f);

    byte value;

    CommandType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
