package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum PodProgressStatus {
    INITIAL_VALUE((byte) 0x00),
    TANK_POWER_ACTIVATED((byte) 0x01),
    TANK_FILL_COMPLETED((byte) 0x02),
    PAIRING_SUCCESS((byte) 0x03),
    PRIMING((byte) 0x04),
    READY_FOR_BASAL_SCHEDULE((byte) 0x05),
    READY_FOR_CANNULA_INSERTION((byte) 0x06),
    CANNULA_INSERTING((byte) 0x07),
    RUNNING_ABOVE_FIFTY_UNITS((byte) 0x08),
    RUNNING_BELOW_FIFTY_UNITS((byte) 0x09),
    ONE_NOT_USED_BUT_IN_33((byte) 0x0a),
    TWO_NOT_USED_BUT_IN_33((byte) 0x0b),
    THREE_NOT_USED_BUT_IN_33((byte) 0x0c),
    FAULT_EVENT_OCCURRED((byte) 0x0d),
    FAILED_TO_INITIALIZE_IN_TIME((byte) 0x0e),
    INACTIVE((byte) 0x0f);

    private byte value;

    PodProgressStatus(byte value) {
        this.value = value;
    }

    public static PodProgressStatus fromByte(byte value) {
        for (PodProgressStatus type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PodProgressStatus: " + value);
    }

    public byte getValue() {
        return value;
    }

    public boolean isReadyForDelivery() {
        return this == RUNNING_ABOVE_FIFTY_UNITS || this == RUNNING_BELOW_FIFTY_UNITS;
    }
}
