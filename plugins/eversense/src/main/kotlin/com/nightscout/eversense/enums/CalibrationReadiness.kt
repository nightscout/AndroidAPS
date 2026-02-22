package com.nightscout.eversense.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CalibrationReadiness(private val value: Int) {
    /** Calibration can be accepted */
    @SerialName("READY")
    READY(0x00),

    /** Transmitter does not have enough data to do a calibration */
    @SerialName("NOT_ENOUGH_DATA")
    NOT_ENOUGH_DATA(0x01),

    /** Glucose is too high to do a calibration*/
    @SerialName("GLUCOSE_TOO_HIGH")
    GLUCOSE_TOO_HIGH(0x02),

    /** A calibration has already be done in the past 2h */
    @SerialName("TOO_SOON")
    TOO_SOON(0x03),

    /** Transmitter is in Dropout phase */
    @SerialName("DROPOUT_PHASE")
    DROPOUT_PHASE(0x04),

    /** Implant is in EOL */
    @SerialName("SENSOR_EOL")
    SENSOR_EOL(0x05),

    /** No implant is linked to transmitter */
    @SerialName("NO_SENSOR_LINKED")
    NO_SENSOR_LINKED(0x06),

    /** Transmitter is in unsupported state */
    @SerialName("UNSUPPORTED_MODE")
    UNSUPPORTED_MODE(0x07),

    /** Transmitter is currently calibrating already */
    @SerialName("CALIBRATING")
    CALIBRATING(0x08),

    /** Transmitter disconnect detected */
    @SerialName("LED_DISCONNECT_DETECTED")
    LED_DISCONNECT_DETECTED(0x09),

    /** Transmitter is in EOL */
    @SerialName("TRANSMITTER_EOL")
    TRANSMITTER_EOL(0x0A),

    @SerialName("UNKNOWN")
    UNKNOWN(0xFF);

    companion object {
        fun from(value: Int): CalibrationReadiness {
            return when(value) {
                0 -> READY
                1 -> NOT_ENOUGH_DATA
                2 -> GLUCOSE_TOO_HIGH
                3 -> TOO_SOON
                4 -> DROPOUT_PHASE
                5 -> SENSOR_EOL
                6 -> NO_SENSOR_LINKED
                7 -> UNSUPPORTED_MODE
                8 -> CALIBRATING
                9 -> LED_DISCONNECT_DETECTED
                10 -> TRANSMITTER_EOL
                else -> UNKNOWN
            }
        }
    }
}