package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class EversenseRuntimeKey(
    override val key: String,
    override val defaultValue: String
) : StringNonPreferenceKey {
    DeviceState("eversense_device_state_json", "{}"),
    CloudQueue("eversense_cloud_queue_json", "[]");

    override val exportable = false
}
