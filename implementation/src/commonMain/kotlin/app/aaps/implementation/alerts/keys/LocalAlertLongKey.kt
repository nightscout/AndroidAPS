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
 *
 * The glucose alarm keys (low, high, rapid fall) are the same kind of deadline: they hold back a
 * repeat of the same alarm, and 0 means "may fire now". [HypoTreatedGlucose] belongs to the low
 * alarm hold and is device state in the same way.
 *
 * The CGM sensor alarm keys hold the episode an alarm was last raised for: the timestamp of that
 * sensor state (for a failed sensor, the failure reason + 1). 0 means "not raised". Within one run
 * of the app the same episode is raised only once. After an app restart an episode that is still
 * going on is raised once more, because the notification itself did not survive the restart: an
 * urgent one with its sound, the others without sound.
 */
enum class LocalAlertLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = false
) : LongNonPreferenceKey {

    NextPumpDisconnectedAlarm("nextPumpDisconnectedAlarm", 0L),
    NextMissedReadingsAlarm("nextMissedReadingsAlarm", 0L),
    NextHypoAlarm("nextHypoAlarm", 0L),
    NextHyperAlarm("nextHyperAlarm", 0L),
    NextRapidFallAlarm("nextRapidFallAlarm", 0L),

    /**
     * Glucose in mg/dL (rounded) when the user tapped "hypo treated", kept with that hold. A reading
     * more than 10 mg/dL below it breaks the hold. 0 means no such hold.
     */
    HypoTreatedGlucose("hypoTreatedGlucose", 0L),

    CgmSensorFailedEpisode("cgmSensorFailedEpisode", 0L),
    CgmSensorExpiredEpisode("cgmSensorExpiredEpisode", 0L),
    CgmSensorExpiringEpisode("cgmSensorExpiringEpisode", 0L),
    CgmSignalLostEpisode("cgmSignalLostEpisode", 0L),
    CgmWarmupDoneEpisode("cgmWarmupDoneEpisode", 0L),
    CgmSecondSensorReadyEpisode("cgmSecondSensorReadyEpisode", 0L)
}