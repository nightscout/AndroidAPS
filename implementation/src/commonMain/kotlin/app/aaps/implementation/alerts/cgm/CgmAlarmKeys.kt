package app.aaps.implementation.alerts.cgm

import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Which preference keys hold the settings of each CGM alarm, and how they are read.
 *
 * The low, high and fall rate alarms keep the keys they had before the CGM alarms existed
 * ([BooleanKey.AlertHypo], [BooleanKey.AlertHyper], [BooleanKey.AlertRapidFall] and their levels), so
 * the settings of a user who had them on are kept.
 */
object CgmAlarmKeys {

    /** The keys of one alarm. Null where the alarm has no such setting. */
    data class Keys(
        val enabled: BooleanKey,
        val repeat: IntKey? = null,
        val sound: StringKey? = null,
        val delay: IntKey? = null,
    )

    fun of(type: CgmAlarmType): Keys = when (type) {
        CgmAlarmType.URGENT_LOW          -> Keys(BooleanKey.AlertUrgentLow, IntKey.AlertUrgentLowRepeat, StringKey.AlertUrgentLowSound)
        CgmAlarmType.URGENT_LOW_SOON     -> Keys(BooleanKey.AlertUrgentLowSoon, IntKey.AlertUrgentLowSoonRepeat, StringKey.AlertUrgentLowSoonSound)
        CgmAlarmType.LOW                 -> Keys(BooleanKey.AlertHypo, IntKey.AlertHypoRepeat, StringKey.AlertHypoSound)
        CgmAlarmType.FALL_RATE           -> Keys(BooleanKey.AlertRapidFall, IntKey.AlertRapidFallRepeat, StringKey.AlertRapidFallSound)
        CgmAlarmType.HIGH                -> Keys(BooleanKey.AlertHyper, IntKey.AlertHyperRepeat, StringKey.AlertHyperSound, IntKey.AlertHyperDelay)
        CgmAlarmType.RISE_RATE           -> Keys(BooleanKey.AlertRapidRise, IntKey.AlertRapidRiseRepeat, StringKey.AlertRapidRiseSound)
        CgmAlarmType.SENSOR_FAILED       -> Keys(BooleanKey.AlertCgmSensorFailed, IntKey.AlertCgmSensorFailedRepeat, StringKey.AlertCgmSensorFailedSound)
        CgmAlarmType.SENSOR_ENDED        -> Keys(BooleanKey.AlertCgmSensorExpired, IntKey.AlertCgmSensorExpiredRepeat, StringKey.AlertCgmSensorExpiredSound)
        CgmAlarmType.SIGNAL_LOSS         ->
            Keys(BooleanKey.AlertCgmSignalLost, IntKey.AlertCgmSignalLostRepeat, StringKey.AlertCgmSignalLostSound, IntKey.AlertCgmSignalLostMinutes)

        CgmAlarmType.SENSOR_ISSUE        ->
            Keys(BooleanKey.AlertCgmSensorIssue, IntKey.AlertCgmSensorIssueRepeat, StringKey.AlertCgmSensorIssueSound, IntKey.AlertCgmSensorIssueMinutes)

        CgmAlarmType.SENSOR_ENDING_SOON  -> Keys(BooleanKey.AlertCgmSensorExpiring)
        CgmAlarmType.SECOND_SENSOR_READY -> Keys(BooleanKey.AlertCgmSecondSensorReady)
        CgmAlarmType.WARMUP_DONE         -> Keys(BooleanKey.AlertCgmWarmupDone)
    }

    /** The settings of [type] as the user set them. */
    fun settings(preferences: Preferences, type: CgmAlarmType): CgmAlarmSettings {
        val keys = of(type)
        return CgmAlarmSettings(
            enabled = preferences.get(keys.enabled),
            delayMinutes = keys.delay?.let { preferences.get(it) } ?: 0,
            repeatMinutes = keys.repeat?.let { preferences.get(it) } ?: 0,
            sound = keys.sound?.let { soundOf(type, preferences.get(it)) },
        )
    }

    /**
     * The sound of a stored choice: "none" is no sound, "default" the AAPS sound, else the Trio sound
     * with that file name. An unknown value is the AAPS sound, so a broken setting never makes an
     * alarm silent. Urgent low always has a sound.
     */
    fun soundOf(type: CgmAlarmType, choice: String): AlarmSound? {
        val aapsSound = if (type == CgmAlarmType.URGENT_LOW) AlarmSound.URGENT_ALARM else AlarmSound.ALARM
        return when (choice) {
            NONE    -> if (type == CgmAlarmType.URGENT_LOW) aapsSound else null
            DEFAULT -> aapsSound
            else    -> AlarmSound.entries.firstOrNull { it.isTrio && it.fileName == choice } ?: aapsSound
        }
    }

    const val NONE = "none"
    const val DEFAULT = "default"
}
