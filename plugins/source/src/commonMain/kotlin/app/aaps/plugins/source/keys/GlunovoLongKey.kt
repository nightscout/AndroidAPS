package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class GlunovoLongKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    LastProcessedTimestamp("last_processed_glunovo_timestamp", 0)
}