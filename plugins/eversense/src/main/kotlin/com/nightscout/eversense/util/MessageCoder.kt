package com.nightscout.eversense.util

import com.nightscout.eversense.enums.EversenseAlarm

object MessageCoder {

    fun messageCodeForGlucoseLevelAlarmFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.LOW_GLUCOSE
        2 -> EversenseAlarm.HIGH_GLUCOSE
        else -> null
    }

    fun messageCodeForGlucoseLevelAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.LOW_GLUCOSE
        2 -> EversenseAlarm.HIGH_GLUCOSE
        else -> null
    }

    fun messageCodeForRateAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.RATE_FALLING
        2 -> EversenseAlarm.RATE_RISING
        else -> null
    }

    fun messageCodeForPredictiveAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.PREDICTIVE_LOW
        4 -> EversenseAlarm.PREDICTIVE_HIGH
        else -> null
    }

    fun messageCodeForSensorHardwareAndAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.UNKNOWN
        2 -> EversenseAlarm.SENSOR_AWOL
        4 -> EversenseAlarm.UNKNOWN
        8 -> EversenseAlarm.UNKNOWN
        16 -> EversenseAlarm.UNKNOWN
        32 -> EversenseAlarm.UNKNOWN
        64 -> EversenseAlarm.UNKNOWN
        128 -> EversenseAlarm.UNKNOWN
        else -> null
    }

    fun messageCodeForSensorReadAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.SERIOUSLY_HIGH
        2 -> EversenseAlarm.SERIOUSLY_LOW
        4 -> EversenseAlarm.UNKNOWN
        8 -> EversenseAlarm.UNKNOWN
        16 -> EversenseAlarm.SENSOR_TEMPERATURE
        32 -> EversenseAlarm.SENSOR_LOW_TEMPERATURE
        64 -> EversenseAlarm.READER_TEMPERATURE
        128 -> EversenseAlarm.MSP_ALARM
        else -> null
    }

    fun messageCodeForSensorReplacementFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.SENSOR_RETIRED
        2 -> EversenseAlarm.SENSOR_RETIRING_SOON_1
        4 -> EversenseAlarm.SENSOR_RETIRING_SOON_1
        8 -> EversenseAlarm.SENSOR_RETIRING_SOON_3
        16 -> EversenseAlarm.SENSOR_RETIRING_SOON_4
        32 -> EversenseAlarm.SENSOR_RETIRING_SOON_5
        64 -> EversenseAlarm.SENSOR_RETIRING_SOON_6
        128 -> EversenseAlarm.UNKNOWN
        else -> null
    }

    fun messageCodeForSensorCalibrationFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.CALIBRATION_GRACE_PERIOD
        2 -> EversenseAlarm.CALIBRATION_EXPIRED
        4 -> EversenseAlarm.CALIBRATION_GRACE_PERIOD
        16 -> EversenseAlarm.CALIBRATION_GRACE_PERIOD
        32 -> EversenseAlarm.CALIBRATION_GRACE_PERIOD
        64 -> EversenseAlarm.CALIBRATION_GRACE_PERIOD
        128 -> EversenseAlarm.CALIBRATION_GRACE_PERIOD
        else -> null
    }

    fun messageCodeForTransmitterStatusAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.CRITICAL_FAULT
        4 -> EversenseAlarm.INVALID_SENSOR
        8 -> EversenseAlarm.INVALID_CLOCK
        32 -> EversenseAlarm.VIBRATION_CURRENT
        64 -> EversenseAlarm.UNKNOWN
        128 -> EversenseAlarm.UNKNOWN
        else -> null
    }

    fun messageCodeForTransmitterBatteryAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.EMPTY_BATTERY
        2 -> EversenseAlarm.VERY_LOW_BATTERY
        4 -> EversenseAlarm.EMPTY_BATTERY
        8 -> EversenseAlarm.BATTERY_ERROR
        else -> null
    }

    fun messageCodeForTransmitterEOLAlertFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.TRANSMITTER_EOL_396
        2 -> EversenseAlarm.TRANSMITTER_EOL_366
        4 -> EversenseAlarm.TRANSMITTER_EOL_330
        8 -> EversenseAlarm.TRANSMITTER_EOL_395
        else -> null
    }

    fun messageCodeForSensorReplacementFlags2(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.SENSOR_RETIRING_SOON_7
        else -> null
    }

    fun messageCodeForCalibrationSwitchFlags(value: Int): EversenseAlarm? = when (value) {
        1 -> EversenseAlarm.ONE_CAL
        2 -> EversenseAlarm.TWO_CAL
        else -> null
    }
}
