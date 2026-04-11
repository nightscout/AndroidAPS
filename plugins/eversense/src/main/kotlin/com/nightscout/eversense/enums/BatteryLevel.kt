package com.nightscout.eversense.enums

enum class BatteryLevel(val code: Int) {
    PERCENTAGE_0(0),
    PERCENTAGE_5(1),
    PERCENTAGE_10(2),
    PERCENTAGE_25(3),
    PERCENTAGE_35(4),
    PERCENTAGE_45(5),
    PERCENTAGE_55(6),
    PERCENTAGE_65(7),
    PERCENTAGE_75(8),
    PERCENTAGE_85(9),
    PERCENTAGE_95(10),
    PERCENTAGE_100(11),
    UNKNOWN(255);

    fun toPercentage(): Int = when (this) {
        PERCENTAGE_0 -> 0
        PERCENTAGE_5 -> 5
        PERCENTAGE_10 -> 10
        PERCENTAGE_25 -> 25
        PERCENTAGE_35 -> 35
        PERCENTAGE_45 -> 45
        PERCENTAGE_55 -> 55
        PERCENTAGE_65 -> 65
        PERCENTAGE_75 -> 75
        PERCENTAGE_85 -> 85
        PERCENTAGE_95 -> 95
        PERCENTAGE_100 -> 100
        UNKNOWN -> -1
    }

    companion object {
        fun from(code: Int): BatteryLevel =
            values().firstOrNull { it.code == code } ?: UNKNOWN
    }
}
