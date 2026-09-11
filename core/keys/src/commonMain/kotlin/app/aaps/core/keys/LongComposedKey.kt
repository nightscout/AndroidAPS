package app.aaps.core.keys

import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey

enum class LongComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongComposedNonPreferenceKey {

    AppExpiration("app_expiration_", "%s", 0L),

    NotificationSnoozedTo("snoozedTo", "%s", 0L),
    ActivityMonitorStart("Monitor_start_", "%s", 0L),
    ActivityMonitorResumed("Monitor_resumed_", "%s", 0L),
    ActivityMonitorTotal("Monitor_total_", "%s", 0L),

    // Per-key last-modified stamp for bidirectionally-synced preferences (keyed by the pref's own
    // key string). Drives last-writer-wins on the master. Internal sync bookkeeping — not exported.
    SyncedPrefModified("synced_pref_modified_", "%s", 0L, exportable = false),
    ;

}