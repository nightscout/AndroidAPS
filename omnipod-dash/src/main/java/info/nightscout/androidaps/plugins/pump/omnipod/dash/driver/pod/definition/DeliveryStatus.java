package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public enum DeliveryStatus {
    SUSPENDED((byte) 0x00),
    BASAL_ACTIVE((byte) 0x01),
    TEMP_BASAL_ACTIVE((byte) 0x02),
    PRIMING((byte) 0x04),
    BOLUS_AND_BASAL_ACTIVE((byte) 0x05),
    BOLUS_AND_TEMP_BASAL_ACTIVE((byte) 0x06),
    UNKNOWN((byte) 0xff);

    private byte value;

    DeliveryStatus(byte value) {
        this.value = value;
    }

    public static DeliveryStatus byValue(byte value) {
        for (DeliveryStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
