package app.aaps.pump.omnipod.eros.driver.definition;

import androidx.annotation.NonNull;

public enum PodProgressStatus {
    INITIALIZED((byte) 0x00),
    MEMORY_INITIALIZED((byte) 0x01),
    REMINDER_INITIALIZED((byte) 0x02),
    PAIRING_COMPLETED((byte) 0x03),
    PRIMING((byte) 0x04),
    PRIMING_COMPLETED((byte) 0x05),
    BASAL_INITIALIZED((byte) 0x06),
    INSERTING_CANNULA((byte) 0x07),
    ABOVE_FIFTY_UNITS((byte) 0x08),
    FIFTY_OR_LESS_UNITS((byte) 0x09),
    ONE_NOT_USED((byte) 0x0a),
    TWO_NOT_USED((byte) 0x0b),
    THREE_NOT_USED((byte) 0x0c),
    FAULT_EVENT_OCCURRED((byte) 0x0d), // Fault event occurred (a "screamer")
    ACTIVATION_TIME_EXCEEDED((byte) 0x0e), // Took > 2 hours from progress 2 to 3 or > 1 hour from 3 to 8
    INACTIVE((byte) 0x0f); // Pod deactivated or a fatal packet state error

    private final byte value;

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

    public boolean isAddressAssigned() {
        return this.isAtLeast(REMINDER_INITIALIZED);
    }

    public boolean isRunning() {
        return this == ABOVE_FIFTY_UNITS || this == FIFTY_OR_LESS_UNITS;
    }

    // TODO there must be a better name for this... but I can't think of it
    public boolean isDead() {
        return this.isAtLeast(FAULT_EVENT_OCCURRED);
    }

    public boolean isAtMost(PodProgressStatus other) {
        return value <= other.value;
    }

    public boolean isBefore(@NonNull PodProgressStatus other) {
        return value < other.value;
    }

    public boolean isAtLeast(PodProgressStatus other) {
        return this.ordinal() >= other.ordinal();
    }

    public boolean isAfter(PodProgressStatus other) {
        return this.ordinal() > other.ordinal();
    }
}
