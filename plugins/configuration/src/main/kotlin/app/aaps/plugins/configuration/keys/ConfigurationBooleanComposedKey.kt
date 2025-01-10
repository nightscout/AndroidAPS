package app.aaps.plugins.configuration.keys

import app.aaps.core.keys.BooleanComposedNonPreferenceKey

enum class ConfigurationBooleanComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
) : BooleanComposedNonPreferenceKey {

    ConfigBuilderEnabled(key = "ConfigBuilder_", format = "%s_%s_Enabled", defaultValue = false),
    ConfigBuilderVisible(key = "ConfigBuilder_", format = "%s_%s_Visible", defaultValue = false),
}
