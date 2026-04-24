package com.nightscout.eversense.enums

enum class EversenseAlarm(val code: Int) {
    CRITICAL_FAULT(0), SENSOR_RETIRED(1), EMPTY_BATTERY(2),
    SENSOR_TEMPERATURE(3), SENSOR_LOW_TEMPERATURE(4), READER_TEMPERATURE(5),
    SENSOR_AWOL(6), INVALID_SENSOR(8), CALIBRATION_REQUIRED(11),
    SERIOUSLY_LOW(12), SERIOUSLY_HIGH(13), LOW_GLUCOSE(14), HIGH_GLUCOSE(15),
    PREDICTIVE_LOW(18), PREDICTIVE_HIGH(19), RATE_FALLING(20), RATE_RISING(21),
    CALIBRATION_GRACE_PERIOD(22), CALIBRATION_EXPIRED(23),
    SENSOR_RETIRING_SOON_1(24), SENSOR_RETIRING_SOON_3(26),
    SENSOR_RETIRING_SOON_4(27), SENSOR_RETIRING_SOON_5(28),
    SENSOR_RETIRING_SOON_6(29), SENSOR_RETIRING_SOON_7(53),
    VERY_LOW_BATTERY(31), INVALID_CLOCK(33), SENSOR_STABILITY(34),
    TRANSMITTER_DISCONNECTED(35), VIBRATION_CURRENT(36), MSP_ALARM(45),
    CALIBRATION_FAILED(47), CALIBRATION_SUSPICIOUS(48), CALIBRATION_NOW(49),
    TRANSMITTER_EOL_396(50), TRANSMITTER_EOL_366(51), BATTERY_ERROR(52),
    TRANSMITTER_EOL_330(55), TRANSMITTER_EOL_395(56), ONE_CAL(57),
    CALIBRATION_SUSPICIOUS_2(59), BATTERY_STATUS(60), SENSOR_CONNECTION(62),
    EARLY_SENSOR_RETIREMENT(64), GENERAL_GLUCOSE_SUSPENDED(65),
    SENSOR_GRACE(66), SENSOR_SYNC_CONFIRMED(67), TX_DOCKED(68),
    TX_UNDOCKED(69), TWO_CAL(90), UNKNOWN(255);

    val title: String get() = when (this) {
        CRITICAL_FAULT -> "Transmitter Error"
        SENSOR_RETIRED, SENSOR_GRACE, SENSOR_RETIRING_SOON_1, SENSOR_RETIRING_SOON_3,
        SENSOR_RETIRING_SOON_4, SENSOR_RETIRING_SOON_5, SENSOR_RETIRING_SOON_6,
        SENSOR_RETIRING_SOON_7 -> "Sensor Replacement"
        EMPTY_BATTERY -> "Battery Empty"
        SENSOR_TEMPERATURE -> "High Sensor Temperature"
        SENSOR_LOW_TEMPERATURE -> "Low Sensor Temperature"
        READER_TEMPERATURE -> "High Transmitter Temperature"
        SENSOR_AWOL -> "No Sensor Detected"
        INVALID_SENSOR -> "New Sensor Detected"
        CALIBRATION_REQUIRED -> "Calibrate Now"
        SERIOUSLY_LOW -> "Out of Range Low Glucose"
        SERIOUSLY_HIGH -> "Out of Range High Glucose"
        LOW_GLUCOSE -> "Low Glucose"
        HIGH_GLUCOSE -> "High Glucose"
        PREDICTIVE_LOW -> "Predicted Low Glucose"
        PREDICTIVE_HIGH -> "Predicted High Glucose"
        RATE_FALLING -> "Rate Falling"
        RATE_RISING -> "Rate Rising"
        CALIBRATION_GRACE_PERIOD -> "Calibration Past Due"
        CALIBRATION_EXPIRED -> "Calibration Expired"
        VERY_LOW_BATTERY -> "Low Battery"
        INVALID_CLOCK -> "Invalid Transmitter Time"
        TRANSMITTER_DISCONNECTED -> "Transmitter Disconnected"
        VIBRATION_CURRENT -> "Vibration Motor"
        MSP_ALARM -> "Sensor Replacement"
        CALIBRATION_FAILED -> "Calibrate Again"
        CALIBRATION_SUSPICIOUS -> "New Calibration Needed"
        CALIBRATION_NOW, CALIBRATION_SUSPICIOUS_2 -> "Calibrate Now"
        TRANSMITTER_EOL_330, TRANSMITTER_EOL_366, TRANSMITTER_EOL_395,
        TRANSMITTER_EOL_396 -> "Transmitter Replacement"
        BATTERY_ERROR -> "Battery Error"
        ONE_CAL -> "1 Weekly Calibration Phase"
        TWO_CAL -> "2 Daily Calibration Phase"
        BATTERY_STATUS -> "Battery Status"
        SENSOR_CONNECTION -> "Sensor Connection"
        EARLY_SENSOR_RETIREMENT -> "Sensor Retirement Area"
        GENERAL_GLUCOSE_SUSPENDED -> "Glucose Suspend"
        SENSOR_SYNC_CONFIRMED -> "Sensor Sync Confirmed"
        TX_DOCKED -> "Transmitter Inactive"
        TX_UNDOCKED -> "Transmitter Active"
        SENSOR_STABILITY -> "Sensor Stability"
        UNKNOWN -> "Unknown Error"
    }

    val isCritical: Boolean get() = this in listOf(
        CALIBRATION_REQUIRED, CALIBRATION_EXPIRED, BATTERY_ERROR,
        READER_TEMPERATURE, SENSOR_TEMPERATURE, SENSOR_LOW_TEMPERATURE
    )

    val isWarning: Boolean get() = this in listOf(
        CALIBRATION_NOW, CALIBRATION_FAILED
    )

    companion object {
        fun from(code: Int): EversenseAlarm =
            values().firstOrNull { it.code == code } ?: UNKNOWN
    }
}
