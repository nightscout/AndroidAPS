package app.aaps.core.data.pump.defs

enum class BasalUpdateResult {
    SUCCESS,     // Basal rate was set on the pump
    FAILED,      // The wanted rate is above the pump's maximum basal rate
    DISCONNECTED // Bluetooth dropped before the pump answered
}