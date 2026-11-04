package app.aaps.plugins.sync.tidepool.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/**
 * How far THIS install has uploaded to Tidepool. A cursor, so not exportable - another phone's
 * position would make this one skip a stretch of records or re-send it.
 *
 * Note this is only the cursor. The Tidepool OAuth state beside it stays exportable on purpose: a
 * token is issued to the account, survives a transfer, and re-authorising on a new phone is exactly
 * the friction a settings backup exists to remove.
 */
enum class TidepoolLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = false
) : LongNonPreferenceKey {

    LastEnd("tidepool_last_end", 0L)
}