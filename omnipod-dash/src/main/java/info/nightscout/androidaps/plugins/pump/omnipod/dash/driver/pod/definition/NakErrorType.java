package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public enum NakErrorType {
    FLASH_WRITE((byte) 0x01),
    FLASH_ERASE((byte) 0x02),
    FLASH_OPERATION((byte) 0x03),
    FLASH_ADDR((byte) 0x04),
    POD_STATE((byte) 0x05),
    CRITICAL_VARIABLE((byte) 0x06),
    ILLEGAL_PARAM((byte) 0x07),
    BOLUS_CRITICAL_VAR((byte) 0x08),
    INT_ILLEGAL_PARAM((byte) 0x09),
    ILLEGAL_CHECKSUM((byte) 0x0a),
    INVALID_MSG_LEN((byte) 0x0b),
    PUMP_STATE((byte) 0x0c),
    ILLEGAL_COMMAND((byte) 0x0d),
    ILLEGAL_FILL_STATE((byte) 0x0e),
    MAX_READWRITE_SIZE((byte) 0x0f),
    ILLEGAL_READ_ADDRESS((byte) 0x10),
    ILLEGAL_READ_MEM_TYPE((byte) 0x11),
    INIT_POD((byte) 0x12),
    ILLEGAL_CMD_STATE((byte) 0x13),
    ILLEGAL_SECURITY_CODE((byte) 0x14),
    POD_IN_ALARM((byte) 0x15),
    COMD_NOT_SET((byte) 0x16),
    ILLEGAL_RX_SENS_VALUE((byte) 0x17),
    ILLEGAL_TX_PKT_SIZE((byte) 0x18),
    OCCL_PARAMS_ALREADY_SET((byte) 0x19),
    OCCL_PARAM((byte) 0x1a),
    ILLEGAL_CDTHR_VALUE((byte) 0x1b),
    IGNORE_COMMAND((byte) 0x1c),
    INVALID_CRC((byte) 0x1d),
    UNKNOWN((byte) 0xff);

    private byte value;

    NakErrorType(byte value) {
        this.value = value;
    }

    public static NakErrorType byValue(byte value) {
        for (NakErrorType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
