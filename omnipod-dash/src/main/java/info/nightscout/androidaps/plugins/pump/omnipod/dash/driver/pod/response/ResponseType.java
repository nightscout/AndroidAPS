package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

public enum ResponseType {
    ACTIVATION_RESPONSE((byte) 0x01),
    DEFAULT_STATUS_RESPONSE((byte) 0x1d),
    ADDITIONAL_STATUS_RESPONSE((byte) 0x02),
    NAK_RESPONSE((byte) 0x06),
    UNKNOWN((byte) 0xff);

    private byte value;

    ResponseType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static ResponseType byValue(byte value) {
        for (ResponseType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }

    enum AdditionalStatusResponseType {
        STATUS_RESPONSE_PAGE_1((byte) 0x01),
        ALARM_STATUS((byte) 0x02),
        STATUS_RESPONSE_PAGE_3((byte) 0x03),
        STATUS_RESPONSE_PAGE_5((byte) 0x05),
        STATUS_RESPONSE_PAGE_6((byte) 0x06),
        STATUS_RESPONSE_PAGE_70((byte) 0x46),
        STATUS_RESPONSE_PAGE_80((byte) 0x50),
        STATUS_RESPONSE_PAGE_81((byte) 0x51),
        UNKNOWN((byte) 0xff);

        private byte value;

        AdditionalStatusResponseType(byte value) {
            this.value = value;
        }

        public static AdditionalStatusResponseType byValue(byte value) {
            for (AdditionalStatusResponseType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return UNKNOWN;
        }

        public byte getValue() {
            return value;
        }
    }

    enum ActivationResponseType {
        GET_VERSION_RESPONSE((byte) 0x15),
        SET_UNIQUE_ID_RESPONSE((byte) 0x1b),
        UNKNOWN((byte) 0xff);

        private byte length;

        ActivationResponseType(byte length) {
            this.length = length;
        }

        public static ActivationResponseType byLength(byte length) {
            for (ActivationResponseType type : values()) {
                if (type.length == length) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
}
