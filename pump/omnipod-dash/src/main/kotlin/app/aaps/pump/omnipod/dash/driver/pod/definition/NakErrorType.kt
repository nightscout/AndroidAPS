package app.aaps.pump.omnipod.dash.driver.pod.definition

import app.aaps.pump.omnipod.dash.driver.pod.util.HasValue

enum class NakErrorType(override val value: Byte) : HasValue {

    FLASH_WRITE(0x01.toByte()),
    FLASH_ERASE(0x02.toByte()),
    FLASH_OPERATION(0x03.toByte()),
    FLASH_ADDR(0x04.toByte()),
    POD_STATE(0x05.toByte()),
    CRITICAL_VARIABLE(0x06.toByte()),
    ILLEGAL_PARAM(0x07.toByte()),
    BOLUS_CRITICAL_VAR(0x08.toByte()),
    INT_ILLEGAL_PARAM(0x09.toByte()),
    ILLEGAL_CHECKSUM(0x0a.toByte()),
    INVALID_MSG_LEN(0x0b.toByte()),
    PUMP_STATE(0x0c.toByte()),
    ILLEGAL_COMMAND(0x0d.toByte()),
    ILLEGAL_FILL_STATE(0x0e.toByte()),
    MAX_READWRITE_SIZE(0x0f.toByte()),
    ILLEGAL_READ_ADDRESS(0x10.toByte()),
    ILLEGAL_READ_MEM_TYPE(0x11.toByte()),
    INIT_POD(0x12.toByte()),
    ILLEGAL_CMD_STATE(0x13.toByte()),
    ILLEGAL_SECURITY_CODE(0x14.toByte()),
    POD_IN_ALARM(0x15.toByte()),
    COMD_NOT_SET(0x16.toByte()),
    ILLEGAL_RX_SENS_VALUE(0x17.toByte()),
    ILLEGAL_TX_PKT_SIZE(0x18.toByte()),
    OCCL_PARAMS_ALREADY_SET(0x19.toByte()),
    OCCL_PARAM(0x1a.toByte()),
    ILLEGAL_CDTHR_VALUE(0x1b.toByte()),
    IGNORE_COMMAND(0x1c.toByte()),
    INVALID_CRC(0x1d.toByte()),
    UNKNOWN(0xff.toByte());
}
