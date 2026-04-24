package com.nightscout.eversense.enums

enum class CommandError(val code: Int) {
    NOT_ALLOWED(1),
    UNUSED(2),
    INVALID_COMMAND_CODE(3),
    INVALID_CRC(4),
    INVALID_MESSAGE_LENGTH(5),
    BUFFER_OVERFLOW(6),
    INVALID_COMMAND_ARGUMENT(7),
    SENSOR_READ_ERROR(8),
    LOW_BATTERY_ERROR(9),
    SENSOR_HARDWARE_FAILURE(10),
    TRANSMITTER_HARDWARE_FAILURE(11),
    SENSOR_UNABLE_TO_BE_LINKED(12),
    TRANSMITTER_IS_BUSY(13),
    INVALID_RECORD_NUMBER_RANGE(14),
    INVALID_RECORD(15),
    CORRUPT_RECORD(16),
    CRITICAL_FAULT_ERROR(17),
    CRC_ERROR_LOGICAL_BLOCK(18),
    ACCESS_DENIED(19),
    USB_ONLY(20),
    NO_DATA_AVAILABLE(21),
    GLUCOSE_BLINDED(22);

    companion object {
        fun from(code: Int): CommandError? = values().firstOrNull { it.code == code }
    }
}
