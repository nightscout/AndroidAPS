package app.aaps.plugins.configuration.keys

import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

enum class ConfigurationBooleanComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanComposedNonPreferenceKey {

    ConfigBuilderEnabled(key = "ConfigBuilder_Enabled_", format = "%s", defaultValue = false),
    ConfigBuilderVisible(key = "ConfigBuilder_Visible_", format = "%s", defaultValue = false),
}
