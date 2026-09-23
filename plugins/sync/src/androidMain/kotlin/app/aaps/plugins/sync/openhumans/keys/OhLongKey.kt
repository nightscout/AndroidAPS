package app.aaps.plugins.sync.openhumans.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/**
 * Not all of one kind, so the flag is set per entry rather than on the constructor.
 *
 * [Counter] and [UploadOffset] are cursors - how far THIS install has uploaded - and travel no better
 * than the Nightscout, xDrip and Tidepool ones that were made non-exportable on 2026-09-22: another
 * phone's offset makes this one skip a stretch of records it never uploaded, or re-send them. They
 * were missed in that sweep because they sit on a plugin nobody thought to look at, and found by a
 * later coverage pass.
 *
 * [ExpiresAt] is different and stays exportable: it belongs with the OAuth token beside it in
 * [OhStringKey], which travels on purpose so cloud upload keeps working after a transfer. An expiry
 * without its token would be meaningless, and a token without its expiry would be treated as fresh.
 */
enum class OhLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    Counter("openhumans_counter", 1, exportable = false),
    ExpiresAt("openhumans_expires_at", 0),
    UploadOffset("openhumans_upload_offset", 0, exportable = false),
}
