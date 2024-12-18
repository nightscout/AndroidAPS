package app.aaps.pump.omnipod.eros.driver.definition.schedule;

public enum InsulinScheduleType {
    BASAL_SCHEDULE(0),
    TEMP_BASAL_SCHEDULE(1),
    BOLUS(2);

    private final byte value;

    InsulinScheduleType(int value) {
        this.value = (byte) value;
    }

    public static InsulinScheduleType fromByte(byte input) {
        for (InsulinScheduleType type : values()) {
            if (type.value == input) {
                return type;
            }
        }
        return null;
    }

    public byte getValue() {
        return value;
    }
}
