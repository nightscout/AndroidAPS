package app.aaps.plugins.sync.openhumans.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class OhLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    Counter("openhumans_counter", 1),
    ExpiresAt("openhumans_expires_at", 0),
    UploadOffset("openhumans_upload_offset", 0),
}