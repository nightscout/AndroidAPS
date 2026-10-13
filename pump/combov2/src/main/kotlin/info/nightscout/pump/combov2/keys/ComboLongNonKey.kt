package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

@Suppress("unused")
enum class ComboLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    TbrTimestamp("combov2-tbr-timestamp", 0),
}