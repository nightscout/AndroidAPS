package app.aaps.plugins.sync.tidepool.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class TidepoolLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastEnd("tidepool_last_end", 0L)
}