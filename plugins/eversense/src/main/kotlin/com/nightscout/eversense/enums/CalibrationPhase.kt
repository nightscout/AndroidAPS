package com.nightscout.eversense.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CalibrationPhase(private val value: Int) {

    @SerialName("WARMING_UP")
    WARMING_UP(0x01),

    @SerialName("INITIALIZATION")
    INITIALIZATION(0x02),

    @SerialName("DAILY_CALIBRATION")
    DAILY_CALIBRATION(0x03),

    @SerialName("WEEKLY_CALIBRATION")
    WEEKLY_CALIBRATION(0x08),

    @SerialName("SUSPICIOUS")
    SUSPICIOUS(0x04),

    @SerialName("UNKNOWN")
    UNKNOWN(0x05),

    @SerialName("DEBUG")
    DEBUG(0x06),

    @SerialName("DROPOUT")
    DROPOUT(0x07);

    companion object {
        fun fromE3(value: Int): CalibrationPhase {
            return when(value) {
                1 -> WARMING_UP
                2 -> DAILY_CALIBRATION
                3 -> INITIALIZATION
                4 -> SUSPICIOUS
                5 -> UNKNOWN
                6 -> DEBUG
                7 -> DROPOUT
                else -> UNKNOWN
            }
        }

        // phase = raw CalibrationPhase byte (ordinal from official app CAL_PHASE enum)
        // calPerDay = raw NumberOfCalPerDay byte: 0 = daily, 1 = weekly
        fun from365(phase: Int, calPerDay: Int = 0): CalibrationPhase {
            return when(phase) {
                0 -> UNKNOWN
                1 -> WARMING_UP
                2 -> INITIALIZATION
                3 -> if (calPerDay == 1) WEEKLY_CALIBRATION else DAILY_CALIBRATION
                4 -> SUSPICIOUS
                5 -> DROPOUT
                6 -> DEBUG
                else -> UNKNOWN
            }
        }
    }
}