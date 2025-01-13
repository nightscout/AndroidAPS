package app.aaps.plugins.configuration.keys

import app.aaps.core.keys.BooleanNonPreferenceKey

enum class ConfigurationBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
) : BooleanNonPreferenceKey {

    AllowHardwarePump(key = "allow_hardware_pump", defaultValue = false),
}
