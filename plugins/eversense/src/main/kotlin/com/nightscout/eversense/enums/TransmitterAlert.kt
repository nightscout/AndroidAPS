package com.nightscout.eversense.enums

enum class TransmitterAlert(val code: Int) {
    CRITICAL_FAULT_ALARM(0),
    SENSOR_RETIRED_ALARM(1),
    EMPTY_BATTERY_ALARM(2),
    SENSOR_TEMPERATURE_ALARM(3),
    SENSOR_LOW_TEMPERATURE_ALARM(4),
    READER_TEMPERATURE_ALARM(5),
    SENSOR_AWOL_ALARM(6),
    SENSOR_ERROR_ALARM(7),
    INVALID_SENSOR_ALARM(8),
    HIGH_AMBIENT_LIGHT_ALARM(9),
    RESERVED_1(10),
    SERIOUSLY_LOW_ALARM(12),
    SERIOUSLY_HIGH_ALARM(13),
    LOW_GLUCOSE_ALARM(14),
    HIGH_GLUCOSE_ALARM(15),
    LOW_GLUCOSE_ALERT(16),
    HIGH_GLUCOSE_ALERT(17),
    PREDICTIVE_LOW_ALARM(18),
    PREDICTIVE_HIGH_ALARM(19),
    RATE_FALLING_ALARM(20),
    RATE_RISING_ALARM(21),
    CALIBRATION_GRACE_PERIOD_ALARM(22),
    CALIBRATION_EXPIRED_ALARM(23),
    SENSOR_RETIRING_SOON_1_ALARM(24),
    SENSOR_RETIRING_SOON_2_ALARM(25),
    SENSOR_RETIRING_SOON_3_ALARM(26),
    SENSOR_RETIRING_SOON_4_ALARM(27),
    SENSOR_RETIRING_SOON_5_ALARM(28),
    SENSOR_RETIRING_SOON_6_ALARM(29),
    SENSOR_PREMATURE_REPLACEMENT_ALARM(30),
    VERY_LOW_BATTERY_ALARM(31),
    LOW_BATTERY_ALARM(32),
    INVALID_CLOCK_ALARM(33),
    SENSOR_STABILITY(34),
    TRANSMITTER_DISCONNECTED(35),
    VIBRATION_CURRENT_ALARM(36),
    SENSOR_AGED_OUT_ALARM(37),
    SENSOR_ON_HOLD_ALARM(38),
    MEP_ALARM(39),
    EDR_ALARM_0(40),
    EDR_ALARM_1(41),
    EDR_ALARM_2(42),
    EDR_ALARM_3(43),
    EDR_ALARM_4(44),
    MSP_ALARM(45),
    RESERVED_2(46),
    TRANSMITTER_EOL_396(50),
    TRANSMITTER_EOL_366(51),
    BATTERY_ERROR_ALARM(52),
    SENSOR_RETIRING_SOON_7_ALARM(53),
    RESERVED_3(54),
    TRANSMITTER_EOL_330(55),
    TRANSMITTER_EOL_395(56),
    ONE_CAL(57),
    TWO_CAL(58),
    TRANSMITTER_RECONNECTED(60),
    APP_RESERVED_1(63),
    SYSTEM_TIME(64),
    APP_RESERVED_2(65),
    INCOMPATIBLE_TX(66),
    SENSOR_FILE(67),
    SENSOR_RELINK(68),
    NEW_PASSWORD_DETECTED(69),
    BATTERY_OPTIMIZATION(70),
    NO_ALARM_ACTIVE(71),
    NUMBER_OF_MESSAGES(72);

    val canBlindGlucose: Boolean get() = this in setOf(
        HIGH_GLUCOSE_ALARM, HIGH_GLUCOSE_ALERT,
        LOW_GLUCOSE_ALARM, LOW_GLUCOSE_ALERT,
        PREDICTIVE_HIGH_ALARM, PREDICTIVE_LOW_ALARM,
        RATE_FALLING_ALARM, RATE_RISING_ALARM
    )

    val title: String get() = when (this) {
        CRITICAL_FAULT_ALARM -> "Critical Fault"
        SENSOR_RETIRED_ALARM -> "Sensor Retired"
        EMPTY_BATTERY_ALARM -> "Empty Battery"
        SENSOR_TEMPERATURE_ALARM -> "Sensor High Temperature"
        SENSOR_LOW_TEMPERATURE_ALARM -> "Sensor Low Temperature"
        READER_TEMPERATURE_ALARM -> "Transmitter High Temperature"
        SENSOR_AWOL_ALARM -> "No Sensor Detected"
        SENSOR_ERROR_ALARM -> "Sensor Hardware Error"
        INVALID_SENSOR_ALARM -> "Invalid Sensor"
        HIGH_AMBIENT_LIGHT_ALARM -> "High Ambient Light"
        RESERVED_1 -> "Reserved 1"
        SERIOUSLY_LOW_ALARM -> "Seriously Low Glucose"
        SERIOUSLY_HIGH_ALARM -> "Seriously High Glucose"
        LOW_GLUCOSE_ALARM -> "Low Glucose"
        HIGH_GLUCOSE_ALARM -> "High Glucose"
        LOW_GLUCOSE_ALERT -> "Low Glucose Alert"
        HIGH_GLUCOSE_ALERT -> "High Glucose Alert"
        PREDICTIVE_LOW_ALARM -> "Predicted Low Glucose"
        PREDICTIVE_HIGH_ALARM -> "Predicted High Glucose"
        RATE_FALLING_ALARM -> "Rate Falling"
        RATE_RISING_ALARM -> "Rate Rising"
        CALIBRATION_GRACE_PERIOD_ALARM -> "Calibration Grace Period"
        CALIBRATION_EXPIRED_ALARM -> "Calibration Expired"
        SENSOR_RETIRING_SOON_1_ALARM -> "Sensor Retiring Soon 1"
        SENSOR_RETIRING_SOON_2_ALARM -> "Sensor Retiring Soon 2"
        SENSOR_RETIRING_SOON_3_ALARM -> "Sensor Retiring Soon 3"
        SENSOR_RETIRING_SOON_4_ALARM -> "Sensor Retiring Soon 4"
        SENSOR_RETIRING_SOON_5_ALARM -> "Sensor Retiring Soon 5"
        SENSOR_RETIRING_SOON_6_ALARM -> "Sensor Retiring Soon 6"
        SENSOR_PREMATURE_REPLACEMENT_ALARM -> "Sensor Premature Replacement"
        VERY_LOW_BATTERY_ALARM -> "Very Low Battery"
        LOW_BATTERY_ALARM -> "Low Battery"
        INVALID_CLOCK_ALARM -> "Invalid Clock"
        SENSOR_STABILITY -> "Sensor Instability"
        TRANSMITTER_DISCONNECTED -> "Transmitter Disconnected"
        VIBRATION_CURRENT_ALARM -> "Vibration Motor"
        SENSOR_AGED_OUT_ALARM -> "Sensor Aged Out"
        SENSOR_ON_HOLD_ALARM -> "Sensor Suspend"
        MEP_ALARM -> "MEP Alarm"
        EDR_ALARM_0 -> "EDR Alarm 0"
        EDR_ALARM_1 -> "EDR Alarm 1"
        EDR_ALARM_2 -> "EDR Alarm 2"
        EDR_ALARM_3 -> "EDR Alarm 3"
        EDR_ALARM_4 -> "EDR Alarm 4"
        MSP_ALARM -> "MSP Alarm"
        RESERVED_2 -> "Reserved 2"
        TRANSMITTER_EOL_396 -> "Transmitter EOL 396"
        TRANSMITTER_EOL_366 -> "Transmitter EOL 366"
        BATTERY_ERROR_ALARM -> "Battery Error"
        SENSOR_RETIRING_SOON_7_ALARM -> "Sensor Retiring Soon 7"
        RESERVED_3 -> "Reserved 3"
        TRANSMITTER_EOL_330 -> "Transmitter EOL 330"
        TRANSMITTER_EOL_395 -> "Transmitter EOL 395"
        ONE_CAL -> "1 Daily Calibration Phase"
        TWO_CAL -> "2 Daily Calibrations Phase"
        TRANSMITTER_RECONNECTED -> "Transmitter Reconnected"
        APP_RESERVED_1 -> "App Reserved 1"
        SYSTEM_TIME -> "System Time"
        APP_RESERVED_2 -> "App Reserved 2"
        INCOMPATIBLE_TX -> "Incompatible Transmitter"
        SENSOR_FILE -> "Sensor File"
        SENSOR_RELINK -> "Sensor Re-link"
        NEW_PASSWORD_DETECTED -> "New Password Detected"
        BATTERY_OPTIMIZATION -> "App Performance"
        NO_ALARM_ACTIVE -> "No Alarm Active"
        NUMBER_OF_MESSAGES -> "Number of Messages"
    }

    companion object {
        fun from(code: Int): TransmitterAlert? = values().firstOrNull { it.code == code }
    }
}
