package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum LogEventErrorCode {
    NONE((byte) 0x00),
    IMMEDIATE_BOLUS_IN_PROGRESS((byte) 0x01),
    INTERNAL_2_BIT_VARIABLE_SET_AND_MANIPULATED_IN_MAIN_LOOP_ROUTINES_2((byte) 0x02),
    INTERNAL_2_BIT_VARIABLE_SET_AND_MANIPULATED_IN_MAIN_LOOP_ROUTINES_3((byte) 0x03),
    INSULIN_STATE_TABLE_CORRUPTION((byte) 0x04);

    private byte value;

    LogEventErrorCode(byte value) {
        this.value = value;
    }

    public static LogEventErrorCode fromByte(byte value) {
        for (LogEventErrorCode type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LogEventErrorCode: " + value);
    }
}
