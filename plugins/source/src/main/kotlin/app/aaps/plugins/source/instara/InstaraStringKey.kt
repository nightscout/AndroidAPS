package app.aaps.plugins.source.instara

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

// Instara plugin-local non-preference keys for internal persisted state
enum class InstaraStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    /**
     * Instara per-device meta storage (NO DB schema changes).
     * JSON format (keyed by devicePrefix as string):
     * {
     *   "31000399": {"sgvStart": 3100039900003, "sgvMark": 6048}
     * }
     * - sgvStart: the first sgvId for that device, defined as the sgvId of the first row that carries sgvMark != null.
     * - sgvMark : device-level total mark/count for that device (can differ across devices).
     * exportable=false because it’s internal state derived from device traffic.
     */
    DeviceMetaJson("instara_device_meta_json", "")
}