package app.aaps.plugins.configuration.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class ConfigurationBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanNonPreferenceKey {

    AllowHardwarePump(key = "allow_hardware_pump", defaultValue = false),
}
