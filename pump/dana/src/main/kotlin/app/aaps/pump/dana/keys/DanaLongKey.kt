package app.aaps.pump.dana.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class DanaLongKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    LastClearKeyRequest("rs_last_clear_key_request", 0),
}
