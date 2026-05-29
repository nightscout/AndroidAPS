package com.nightscout.eversense.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CalibrationMode(private val value: Int) {
    @SerialName("DAILY_SINGLE")
    DAILY_SINGLE(0x01),

    @SerialName("DAILY_DUAL")
    DAILY_DUAL(0x02),

    @SerialName("WEEKLY_SINGLE")
    WEEKLY_SINGLE(0x03),

    @SerialName("DEFAULT")
    DEFAULT(0x04);

    companion object {
        fun from365(value: Int): CalibrationMode {
            return when(value) {
                0 -> DAILY_SINGLE
                1 -> WEEKLY_SINGLE
                else -> DEFAULT
            }
        }
    }
}