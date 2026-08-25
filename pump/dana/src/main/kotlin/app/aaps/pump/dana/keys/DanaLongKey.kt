package app.aaps.pump.dana.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class DanaLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastClearKeyRequest("rs_last_clear_key_request", 0),
}
