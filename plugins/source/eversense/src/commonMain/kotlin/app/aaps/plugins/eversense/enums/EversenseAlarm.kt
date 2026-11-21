package app.aaps.plugins.eversense.enums

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.EversenseStrings

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
    SENSOR_GRACE(66), SENSOR_SYNC_CONFIRMED(67),
    // Codes 68/69 (TxDockedAlert/TxUndockedAlert) are deliberately NOT mapped here - they're not
    // real device alarm codes (matches the upstream iOS EversenseKit fix, which removed the
    // identical two cases as "custom error codes in Eversense app"; from(code) below falls back
    // to UNKNOWN for them, which is filtered out at both alarm entry points - see
    // Eversense365Communicator.fullSync (active-alarm list) and EversenseSession's push-alarm
    // handling.
    TWO_CAL(90), UNKNOWN(255);

    val dmsCode: Int get() = when (this) {
        LOW_GLUCOSE -> 0
        HIGH_GLUCOSE -> 1
        RATE_FALLING -> 4
        RATE_RISING -> 5
        PREDICTIVE_LOW -> 6
        PREDICTIVE_HIGH -> 7
        CALIBRATION_NOW -> 8
        CALIBRATION_REQUIRED -> 9
        CALIBRATION_GRACE_PERIOD -> 10
        CALIBRATION_EXPIRED -> 11
        SERIOUSLY_LOW -> 12
        SERIOUSLY_HIGH -> 13
        else -> 255
    }

    /** The alarm as the official app names it, translated. */
    val title: TextRef get() = when (this) {
        CRITICAL_FAULT -> EversenseStrings.eversense_alarm_transmitter_error
        SENSOR_RETIRED, SENSOR_GRACE, SENSOR_RETIRING_SOON_1, SENSOR_RETIRING_SOON_3,
        SENSOR_RETIRING_SOON_4, SENSOR_RETIRING_SOON_5, SENSOR_RETIRING_SOON_6,
        SENSOR_RETIRING_SOON_7 -> EversenseStrings.eversense_alarm_sensor_replacement
        EMPTY_BATTERY -> EversenseStrings.eversense_alarm_battery_empty
        SENSOR_TEMPERATURE -> EversenseStrings.eversense_alarm_sensor_temperature_high
        SENSOR_LOW_TEMPERATURE -> EversenseStrings.eversense_alarm_sensor_temperature_low
        READER_TEMPERATURE -> EversenseStrings.eversense_alarm_transmitter_temperature_high
        SENSOR_AWOL -> EversenseStrings.eversense_alarm_no_sensor
        INVALID_SENSOR -> EversenseStrings.eversense_alarm_new_sensor
        CALIBRATION_REQUIRED -> EversenseStrings.eversense_alarm_calibrate_now
        SERIOUSLY_LOW -> EversenseStrings.eversense_alarm_out_of_range_low
        SERIOUSLY_HIGH -> EversenseStrings.eversense_alarm_out_of_range_high
        LOW_GLUCOSE -> EversenseStrings.eversense_alarm_low_glucose
        HIGH_GLUCOSE -> EversenseStrings.eversense_alarm_high_glucose
        PREDICTIVE_LOW -> EversenseStrings.eversense_alarm_predicted_low
        PREDICTIVE_HIGH -> EversenseStrings.eversense_alarm_predicted_high
        RATE_FALLING -> EversenseStrings.eversense_alarm_rate_falling
        RATE_RISING -> EversenseStrings.eversense_alarm_rate_rising
        CALIBRATION_GRACE_PERIOD -> EversenseStrings.eversense_alarm_calibration_past_due
        CALIBRATION_EXPIRED -> EversenseStrings.eversense_alarm_calibration_expired
        VERY_LOW_BATTERY -> EversenseStrings.eversense_alarm_low_battery
        INVALID_CLOCK -> EversenseStrings.eversense_alarm_invalid_time
        TRANSMITTER_DISCONNECTED -> EversenseStrings.eversense_alarm_transmitter_disconnected
        VIBRATION_CURRENT -> EversenseStrings.eversense_alarm_vibration_motor
        MSP_ALARM -> EversenseStrings.eversense_alarm_sensor_replacement
        CALIBRATION_FAILED -> EversenseStrings.eversense_alarm_calibrate_again
        CALIBRATION_SUSPICIOUS -> EversenseStrings.eversense_alarm_new_calibration_needed
        CALIBRATION_NOW, CALIBRATION_SUSPICIOUS_2 -> EversenseStrings.eversense_alarm_calibrate_now
        TRANSMITTER_EOL_330, TRANSMITTER_EOL_366, TRANSMITTER_EOL_395,
        TRANSMITTER_EOL_396 -> EversenseStrings.eversense_alarm_transmitter_replacement
        BATTERY_ERROR -> EversenseStrings.eversense_alarm_battery_error
        ONE_CAL -> EversenseStrings.eversense_alarm_one_calibration_phase
        TWO_CAL -> EversenseStrings.eversense_alarm_two_calibration_phase
        BATTERY_STATUS -> EversenseStrings.eversense_alarm_battery_status
        SENSOR_CONNECTION -> EversenseStrings.eversense_alarm_sensor_connection
        EARLY_SENSOR_RETIREMENT -> EversenseStrings.eversense_alarm_sensor_retirement_area
        GENERAL_GLUCOSE_SUSPENDED -> EversenseStrings.eversense_alarm_glucose_suspend
        SENSOR_SYNC_CONFIRMED -> EversenseStrings.eversense_alarm_sensor_sync_confirmed
        SENSOR_STABILITY -> EversenseStrings.eversense_alarm_sensor_stability
        UNKNOWN -> EversenseStrings.eversense_alarm_unknown
    }

    val isInfo: Boolean get() = this in listOf(
        BATTERY_STATUS, ONE_CAL, TWO_CAL, SENSOR_SYNC_CONFIRMED,
        SENSOR_RETIRING_SOON_1, SENSOR_RETIRING_SOON_3, SENSOR_RETIRING_SOON_4,
        SENSOR_RETIRING_SOON_5, SENSOR_RETIRING_SOON_6, SENSOR_RETIRING_SOON_7
    )

    val isCritical: Boolean get() = !isWarning && !isInfo

    val isWarning: Boolean get() = this in listOf(
        CALIBRATION_NOW, CALIBRATION_FAILED, CALIBRATION_SUSPICIOUS, CALIBRATION_SUSPICIOUS_2,
        INVALID_CLOCK, INVALID_SENSOR, PREDICTIVE_HIGH, PREDICTIVE_LOW,
        RATE_FALLING, RATE_RISING, TRANSMITTER_EOL_330, TRANSMITTER_EOL_366, TRANSMITTER_EOL_395
    )

    companion object {
        fun from(code: Int): EversenseAlarm =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
