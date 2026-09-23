package app.aaps.implementation.alerts.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/**
 * When the next local alarm is due on THIS phone. Device state, so not exportable.
 *
 * Both are wall-clock deadlines that `LocalAlertUtilsImpl` moves forward as it goes. Another phone's
 * copy is meaningless here and wrong in both directions: a time in the future suppresses an alarm
 * this install should be raising, and one in the past fires it immediately. They are not settings -
 * nothing on a preference screen sets them - so nobody would miss them from a backup.
 *
 * They were missed by the 2026-09-22 sweep that made pump identity and the sync cursors
 * non-exportable; same category, found later by review.
 */
enum class LocalAlertLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = false
) : LongNonPreferenceKey {

    NextPumpDisconnectedAlarm("nextPumpDisconnectedAlarm", 0L),
    NextMissedReadingsAlarm("nextMissedReadingsAlarm", 0L)
}