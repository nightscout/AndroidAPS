package app.aaps.pump.omnipod.eros.driver.definition;

public enum DeliveryStatus {
    SUSPENDED((byte) 0x00),
    NORMAL((byte) 0x01),
    TEMP_BASAL_RUNNING((byte) 0x02),
    PRIMING((byte) 0x04),
    BOLUS_IN_PROGRESS((byte) 0x05),
    BOLUS_AND_TEMP_BASAL((byte) 0x06);

    private final byte value;

    DeliveryStatus(byte value) {
        this.value = value;
    }

    public static DeliveryStatus fromByte(byte value) {
        for (DeliveryStatus type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DeliveryStatus: " + value);
    }

    public byte getValue() {
        return value;
    }

    public boolean isBolusing() {
        return this.equals(BOLUS_IN_PROGRESS) || this.equals(BOLUS_AND_TEMP_BASAL);
    }

    public boolean isTbrRunning() {
        return this.equals(TEMP_BASAL_RUNNING) || this.equals(BOLUS_AND_TEMP_BASAL);
    }
}
