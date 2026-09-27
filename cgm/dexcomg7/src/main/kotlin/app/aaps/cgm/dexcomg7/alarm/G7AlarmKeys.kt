package app.aaps.cgm.dexcomg7.alarm

import app.aaps.cgm.dexcomg7.R
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

/*
 * The alarm settings. There are ten alarms with the same handful of settings each, so the keys are
 * built from [G7AlarmType] rather than written out as enums: one table instead of seventy rows.
 */

class G7AlarmBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val dependency: BooleanPreferenceKey? = null
) : BooleanPreferenceKey

class G7AlarmIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val title: TextRef,
    override val entries: Map<Int, TextRef>,
    override val summary: TextRef? = null,
    override val dependency: BooleanPreferenceKey? = null
) : IntPreferenceKey {

    override val min: Int = entries.keys.min()
    override val max: Int = entries.keys.max()
    override val preferenceType: PreferenceType = PreferenceType.LIST
}

class G7AlarmStringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef,
    override val entries: Map<String, TextRef>,
    override val dependency: BooleanPreferenceKey? = null
) : StringPreferenceKey {

    override val isPassword: Boolean = false
    override val isPin: Boolean = false
    override val preferenceType: PreferenceType = PreferenceType.LIST
}

/** A glucose level. Stored in mg/dL, shown in the user's units. */
class G7AlarmGlucoseKey(
    override val key: String,
    override val defaultValue: Double,
    override val minMgdl: Int,
    override val maxMgdl: Int,
    override val title: TextRef,
    override val dependency: BooleanPreferenceKey? = null
) : UnitDoublePreferenceKey

class G7AlarmActionKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef?,
    override val onClick: () -> Unit,
    override val dependency: BooleanPreferenceKey? = null
) : IntentPreferenceKey {

    override val preferenceType: PreferenceType = PreferenceType.CLICK
}

/** The settings of one alarm. Null where that alarm does not have the setting. */
class G7AlarmTypeKeys(val type: G7AlarmType) {

    private val prefix = "dexcom_g7_alarm_${type.id}"

    /** Null for the alarms Dexcom does not let you turn off. */
    val enabled: G7AlarmBooleanKey? =
        if (type.canTurnOff) G7AlarmBooleanKey("${prefix}_enabled", type.defaultEnabled, TextRef.AndroidRes(R.string.dexcom_g7_alarm_enabled), TextRef.AndroidRes(type.summary))
        else null

    val level: G7AlarmGlucoseKey? = when (type) {
        G7AlarmType.LOW  -> G7AlarmGlucoseKey("${prefix}_level", 70.0, 60, 100, TextRef.AndroidRes(R.string.dexcom_g7_alarm_level), enabled)
        G7AlarmType.HIGH -> G7AlarmGlucoseKey("${prefix}_level", 250.0, 100, 400, TextRef.AndroidRes(R.string.dexcom_g7_alarm_level), enabled)
        else             -> null
    }

    /** mg/dL per minute. Dexcom offers 2 and 3. */
    val rate: G7AlarmIntKey? =
        if (type == G7AlarmType.RISE_RATE || type == G7AlarmType.FALL_RATE)
            G7AlarmIntKey(
                "${prefix}_rate", 2, TextRef.AndroidRes(R.string.dexcom_g7_alarm_rate),
                mapOf(2 to TextRef.AndroidRes(R.string.dexcom_g7_alarm_rate_2), 3 to TextRef.AndroidRes(R.string.dexcom_g7_alarm_rate_3)),
                dependency = enabled
            )
        else null

    val delay: G7AlarmIntKey? = type.delay?.let { delay ->
        G7AlarmIntKey("${prefix}_delay", delay.defaultMinutes, TextRef.AndroidRes(delay.title), durations(delay.choices, R.string.dexcom_g7_alarm_delay_none), dependency = enabled)
    }

    val repeat = G7AlarmIntKey(
        "${prefix}_repeat", type.defaultRepeatMinutes, TextRef.AndroidRes(R.string.dexcom_g7_alarm_repeat),
        durations(G7AlarmType.REPEAT_CHOICES, R.string.dexcom_g7_alarm_repeat_never),
        summary = TextRef.AndroidRes(R.string.dexcom_g7_alarm_repeat_summary),
        dependency = enabled
    )

    val sound = G7AlarmStringKey(
        "${prefix}_sound", type.defaultSound.id, TextRef.AndroidRes(R.string.dexcom_g7_alarm_sound),
        G7AlarmSound.choicesFor(type.family).associate { it.id to TextRef.AndroidRes(it.label) },
        dependency = enabled
    )

    val vibrateFirst = G7AlarmBooleanKey(
        "${prefix}_vibrate_first", false, TextRef.AndroidRes(R.string.dexcom_g7_alarm_vibrate_first),
        TextRef.AndroidRes(R.string.dexcom_g7_alarm_vibrate_first_summary), enabled
    )

    /** Seconds the AAPS watch app vibrates for this alarm by day. 0 = the watch shows it without vibrating. */
    val watchVibration = G7AlarmIntKey(
        "${prefix}_watch_vibration", watchDefault(night = false), TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_vibration),
        WATCH_VIBRATION_CHOICES, summary = TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_vibration_summary), dependency = enabled
    )

    /** The same, during the night hours set in [G7AlarmKeys.WatchNightStart] .. [G7AlarmKeys.WatchNightEnd]. */
    val watchVibrationNight = G7AlarmIntKey(
        "${prefix}_watch_vibration_night", watchDefault(night = true), TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_vibration_night),
        WATCH_VIBRATION_CHOICES, dependency = enabled
    )

    val all: List<PreferenceKey> = listOfNotNull(enabled, level, rate, delay, repeat, sound, vibrateFirst, watchVibration, watchVibrationNight)

    /** Low alarms wake the wearer at night; the rest only tap. */
    private fun watchDefault(night: Boolean): Int = when (type) {
        G7AlarmType.URGENT_LOW, G7AlarmType.URGENT_LOW_SOON, G7AlarmType.LOW -> if (night) 10 else 5
        G7AlarmType.SENSOR_FAILED                                            -> if (night) 7 else 3
        else                                                                 -> if (night) 5 else 3
    }

    private fun durations(minutes: List<Int>, zeroLabel: Int): Map<Int, TextRef> = minutes.associateWith {
        when {
            it == 0      -> TextRef.AndroidRes(zeroLabel)
            it % 60 == 0 -> TextRef.AndroidRes(R.string.dexcom_g7_hours, listOf(it / 60))
            else         -> TextRef.AndroidRes(R.string.dexcom_g7_minutes, listOf(it))
        }
    }
}

/** Watch vibration choices in seconds. */
private val WATCH_VIBRATION_CHOICES: Map<Int, TextRef> =
    mapOf(0 to TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_vibration_off)) +
        listOf(3, 5, 7, 10).associateWith { TextRef.AndroidRes(R.string.dexcom_g7_seconds, listOf(it)) }

/** Whole hours of the day, shown as a time ("22:00"). */
private val HOURS: Map<Int, TextRef> = (0..23).associateWith { TextRef.AndroidRes(R.string.dexcom_g7_hour_of_day, listOf(it)) }

object G7AlarmKeys {

    val AlarmsEnabled = G7AlarmBooleanKey(
        "dexcom_g7_alarms_enabled", true, TextRef.AndroidRes(R.string.dexcom_g7_alarms_enabled), TextRef.AndroidRes(R.string.dexcom_g7_alarms_enabled_summary)
    )

    /** Percent of the alarm stream's range. 0 = leave the phone's alarm volume alone. */
    val MinimumVolume = G7AlarmIntKey(
        "dexcom_g7_alarm_minimum_volume", 70, TextRef.AndroidRes(R.string.dexcom_g7_alarm_minimum_volume),
        mapOf(0 to TextRef.AndroidRes(R.string.dexcom_g7_alarm_minimum_volume_off)) +
            listOf(30, 50, 70, 85, 100).associateWith { TextRef.AndroidRes(R.string.dexcom_g7_alarm_minimum_volume_percent, listOf(it)) },
        summary = TextRef.AndroidRes(R.string.dexcom_g7_alarm_minimum_volume_summary),
        dependency = AlarmsEnabled
    )

    val SoundUntilAcknowledged = G7AlarmBooleanKey(
        "dexcom_g7_alarm_sound_until_acknowledged", false, TextRef.AndroidRes(R.string.dexcom_g7_alarm_sound_until_acknowledged),
        TextRef.AndroidRes(R.string.dexcom_g7_alarm_sound_until_acknowledged_summary), AlarmsEnabled
    )

    /** Use the night watch vibration between the two hours below. */
    val WatchNight = G7AlarmBooleanKey(
        "dexcom_g7_alarm_watch_night", true, TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_night),
        TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_night_summary), AlarmsEnabled
    )

    val WatchNightStart = G7AlarmIntKey(
        "dexcom_g7_alarm_watch_night_start", 22, TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_night_start), HOURS, dependency = WatchNight
    )

    val WatchNightEnd = G7AlarmIntKey(
        "dexcom_g7_alarm_watch_night_end", 7, TextRef.AndroidRes(R.string.dexcom_g7_alarm_watch_night_end), HOURS, dependency = WatchNight
    )

    val types: Map<G7AlarmType, G7AlarmTypeKeys> = G7AlarmType.entries.associateWith { G7AlarmTypeKeys(it) }

    fun of(type: G7AlarmType): G7AlarmTypeKeys = types.getValue(type)

    val global: List<PreferenceKey> = listOf(AlarmsEnabled, MinimumVolume, SoundUntilAcknowledged, WatchNight, WatchNightStart, WatchNightEnd)

    val all: List<PreferenceKey> = global + types.values.flatMap { it.all }
}
