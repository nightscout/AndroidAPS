package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public enum PodStatus {
    UNINITIALIZED((byte) 0x00),
    MFG_TEST((byte) 0x01),
    FILLED((byte) 0x02),
    UID_SET((byte) 0x03),
    ENGAGING_CLUTCH_DRIVE((byte) 0x04),
    CLUTCH_DRIVE_ENGAGED((byte) 0x05),
    BASAL_PROGRAM_RUNNING((byte) 0x06),
    PRIMING((byte) 0x07),
    RUNNING_ABOVE_MIN_VOLUME((byte) 0x08),
    RUNNING_BELOW_MIN_VOLUME((byte) 0x09),
    UNUSED_10((byte) 0x0a),
    UNUSED_11((byte) 0x0b),
    UNUSED_12((byte) 0x0c),
    ALARM((byte) 0x0d),
    LUMP_OF_COAL((byte) 0x0e),
    DEACTIVATED((byte) 0x0f),
    UNKNOWN((byte) 0xff);

    private byte value;

    PodStatus(byte value) {
        this.value = value;
    }

    public static PodStatus byValue(byte value) {
        for (PodStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
