package com.nightscout.eversense.enums

enum class SignalStrength(val rawThreshold: Int, val threshold: Int) {
    NO_SIGNAL(0, 0),
    POOR(350, 350),
    VERY_LOW(500, 395),
    LOW(800, 494),
    GOOD(1300, 705),
    EXCELLENT(1600, 903);

    val title: String get() = when (this) {
        NO_SIGNAL -> "No signal"
        POOR -> "Poor"
        VERY_LOW -> "Very low"
        LOW -> "Low"
        GOOD -> "Good"
        EXCELLENT -> "Excellent"
    }

    companion object {
        fun from365(value: Int): SignalStrength = when {
            value >= 75 -> EXCELLENT
            value >= 48 -> GOOD
            value >= 30 -> LOW
            value >= 28 -> VERY_LOW
            value >= 25 -> POOR
            else -> NO_SIGNAL
        }

        fun fromRaw(value: Int): SignalStrength = when {
            value >= 1600 -> EXCELLENT
            value >= 1300 -> GOOD
            value >= 800 -> LOW
            value >= 500 -> VERY_LOW
            value >= 350 -> POOR
            else -> NO_SIGNAL
        }
    }
}
