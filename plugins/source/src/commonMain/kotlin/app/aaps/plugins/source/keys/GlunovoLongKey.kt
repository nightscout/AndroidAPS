package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class GlunovoLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    /**
     * How far THIS phone has read the Glunovo provider, so it is not exported.
     *
     * The value is a skip filter - `if (timestamp < get(LastProcessedTimestamp)) continue` - so
     * importing another phone's copy makes this one silently skip every reading older than whatever
     * that phone had reached. Missing CGM data, with nothing on screen to say why. It is the same
     * failure the Glunovo/Intelligo shared-key bug caused, arriving by import instead of by collision.
     */
    LastProcessedTimestamp("last_processed_glunovo_timestamp", 0, exportable = false)
}