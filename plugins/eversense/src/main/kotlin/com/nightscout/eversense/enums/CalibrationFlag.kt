package com.nightscout.eversense.enums

enum class CalibrationFlag(val code: Int) {
    NOT_ENTERED_FOR_CALIBRATION(0),
    ACTUALLY_USED_FOR_CALIBRATION(1),
    MARKED_SUSPICIOUS(2),
    GLUCOSE_TOO_LOW_TO_READ(3),
    GLUCOSE_TOO_HIGH_TO_READ(4),
    GLUCOSE_RAPID_CHANGE(5),
    INVALID_TIME(6),
    INSUFFICIENT_DATA(7),
    SENSOR_EOL(8),
    DROPOUT_PHASE(9),
    AUTO_LINK_MODE_ACTIVE(10),
    SENSOR_LED_DISCONNECT(11),
    OTHER_FAILURE(12),
    THIS_ONE_USED_PREVIOUS_ONE_DELETED(13),
    THIS_SUSPICIOUS_PREVIOUS_DELETED(14),
    INSUFFICIENT_DATA_POST_FS_ENTRY(15),
    UNKNOWN_FAILURE(255);

    fun getTitle(): String = when (this) {
        ACTUALLY_USED_FOR_CALIBRATION,
        NOT_ENTERED_FOR_CALIBRATION -> "Calibration accepted"
        MARKED_SUSPICIOUS -> "Suspicious"
        GLUCOSE_TOO_LOW_TO_READ -> "Glucose too low"
        GLUCOSE_TOO_HIGH_TO_READ -> "Glucose too high"
        GLUCOSE_RAPID_CHANGE -> "Glucose changing too fast"
        INVALID_TIME -> "Invalid time"
        INSUFFICIENT_DATA,
        INSUFFICIENT_DATA_POST_FS_ENTRY -> "Insufficient data"
        SENSOR_EOL -> "Sensor End of Life"
        DROPOUT_PHASE -> "Dropout phase"
        AUTO_LINK_MODE_ACTIVE -> "Autolink"
        SENSOR_LED_DISCONNECT -> "Sensor disconnected"
        OTHER_FAILURE -> "Other failure"
        THIS_ONE_USED_PREVIOUS_ONE_DELETED,
        THIS_SUSPICIOUS_PREVIOUS_DELETED -> "Previous calibration deleted"
        UNKNOWN_FAILURE -> "Unknown failure"
    }

    companion object {
        fun from(code: Int): CalibrationFlag =
            values().firstOrNull { it.code == code } ?: UNKNOWN_FAILURE
    }
}
