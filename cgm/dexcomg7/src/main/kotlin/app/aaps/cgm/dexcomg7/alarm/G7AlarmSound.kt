package app.aaps.cgm.dexcomg7.alarm

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import app.aaps.cgm.dexcomg7.R

/**
 * Which alarm a Dexcom sound was made for. Each alarm offers its own family first, then the extra
 * sounds, the same way the Dexcom app does.
 */
enum class G7SoundFamily {

    URGENT_LOW, URGENT_LOW_SOON, LOW, FALL_RATE, HIGH, RISE_RATE, SIGNAL_LOSS, SENSOR_ISSUE, SYSTEM, TECHNICAL
}

/**
 * The alarm sounds taken from the Dexcom app. Every alarm comes in soft, medium and intense, most also
 * in the short classic style, and there is a set of extra sounds any alarm may use.
 *
 * [id] is what the settings store, so it must not change once released.
 */
enum class G7AlarmSound(val id: String, @RawRes val rawRes: Int, @StringRes val label: Int, val family: G7SoundFamily?) {

    URGENT_LOW_SOFT("urgent_low_soft", R.raw.g7_urgent_low_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.URGENT_LOW),
    URGENT_LOW_MEDIUM("urgent_low_medium", R.raw.g7_urgent_low_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.URGENT_LOW),
    URGENT_LOW_INTENSE("urgent_low_intense", R.raw.g7_urgent_low_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.URGENT_LOW),
    URGENT_LOW_CLASSIC("urgent_low_classic", R.raw.g7_urgent_low_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.URGENT_LOW),
    URGENT_LOW_CLASSIC_2("urgent_low_classic_2", R.raw.g7_urgent_low_classic_2, R.string.dexcom_g7_sound_classic_2, G7SoundFamily.URGENT_LOW),

    URGENT_LOW_SOON_SOFT("urgent_low_soon_soft", R.raw.g7_urgent_low_soon_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.URGENT_LOW_SOON),
    URGENT_LOW_SOON_MEDIUM("urgent_low_soon_medium", R.raw.g7_urgent_low_soon_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.URGENT_LOW_SOON),
    URGENT_LOW_SOON_INTENSE("urgent_low_soon_intense", R.raw.g7_urgent_low_soon_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.URGENT_LOW_SOON),
    URGENT_LOW_SOON_CLASSIC("urgent_low_soon_classic", R.raw.g7_urgent_low_soon_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.URGENT_LOW_SOON),

    LOW_SOFT("low_soft", R.raw.g7_low_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.LOW),
    LOW_MEDIUM("low_medium", R.raw.g7_low_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.LOW),
    LOW_INTENSE("low_intense", R.raw.g7_low_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.LOW),
    LOW_CLASSIC("low_classic", R.raw.g7_low_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.LOW),
    LOW_CLASSIC_2("low_classic_2", R.raw.g7_low_classic_2, R.string.dexcom_g7_sound_classic_2, G7SoundFamily.LOW),

    FALL_RATE_SOFT("fall_rate_soft", R.raw.g7_fall_rate_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.FALL_RATE),
    FALL_RATE_MEDIUM("fall_rate_medium", R.raw.g7_fall_rate_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.FALL_RATE),
    FALL_RATE_INTENSE("fall_rate_intense", R.raw.g7_fall_rate_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.FALL_RATE),
    FALL_RATE_CLASSIC("fall_rate_classic", R.raw.g7_fall_rate_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.FALL_RATE),

    HIGH_SOFT("high_soft", R.raw.g7_high_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.HIGH),
    HIGH_MEDIUM("high_medium", R.raw.g7_high_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.HIGH),
    HIGH_INTENSE("high_intense", R.raw.g7_high_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.HIGH),
    HIGH_CLASSIC("high_classic", R.raw.g7_high_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.HIGH),
    HIGH_CLASSIC_2("high_classic_2", R.raw.g7_high_classic_2, R.string.dexcom_g7_sound_classic_2, G7SoundFamily.HIGH),

    RISE_RATE_SOFT("rise_rate_soft", R.raw.g7_rise_rate_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.RISE_RATE),
    RISE_RATE_MEDIUM("rise_rate_medium", R.raw.g7_rise_rate_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.RISE_RATE),
    RISE_RATE_INTENSE("rise_rate_intense", R.raw.g7_rise_rate_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.RISE_RATE),
    RISE_RATE_CLASSIC("rise_rate_classic", R.raw.g7_rise_rate_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.RISE_RATE),

    // The Dexcom app ships the signal loss sounds as copies of the system alert sounds.
    SIGNAL_LOSS_SOFT("signal_loss_soft", R.raw.g7_system_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.SIGNAL_LOSS),
    SIGNAL_LOSS_MEDIUM("signal_loss_medium", R.raw.g7_system_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.SIGNAL_LOSS),
    SIGNAL_LOSS_INTENSE("signal_loss_intense", R.raw.g7_system_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.SIGNAL_LOSS),
    SIGNAL_LOSS_CLASSIC("signal_loss_classic", R.raw.g7_signal_loss_classic, R.string.dexcom_g7_sound_classic, G7SoundFamily.SIGNAL_LOSS),

    SENSOR_ISSUE_SOFT("sensor_issue_soft", R.raw.g7_sensor_issue_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.SENSOR_ISSUE),
    SENSOR_ISSUE_MEDIUM("sensor_issue_medium", R.raw.g7_sensor_issue_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.SENSOR_ISSUE),
    SENSOR_ISSUE_INTENSE("sensor_issue_intense", R.raw.g7_sensor_issue_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.SENSOR_ISSUE),

    SYSTEM_SOFT("system_soft", R.raw.g7_system_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.SYSTEM),
    SYSTEM_MEDIUM("system_medium", R.raw.g7_system_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.SYSTEM),
    SYSTEM_INTENSE("system_intense", R.raw.g7_system_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.SYSTEM),

    TECHNICAL_SOFT("technical_soft", R.raw.g7_technical_soft, R.string.dexcom_g7_sound_soft, G7SoundFamily.TECHNICAL),
    TECHNICAL_MEDIUM("technical_medium", R.raw.g7_technical_medium, R.string.dexcom_g7_sound_medium, G7SoundFamily.TECHNICAL),
    TECHNICAL_INTENSE("technical_intense", R.raw.g7_technical_intense, R.string.dexcom_g7_sound_intense, G7SoundFamily.TECHNICAL),

    EXTRA_BABY_CRY("extra_baby_cry", R.raw.g7_extra_baby_cry, R.string.dexcom_g7_sound_baby_cry, null),
    EXTRA_BEEP("extra_beep", R.raw.g7_extra_beep, R.string.dexcom_g7_sound_beep, null),
    EXTRA_BLAMO_DINGS("extra_blamo_dings", R.raw.g7_extra_blamo_dings, R.string.dexcom_g7_sound_blamo_dings, null),
    EXTRA_BUZZER_ALARM_CLOCK("extra_buzzer_alarm_clock", R.raw.g7_extra_buzzer_alarm_clock, R.string.dexcom_g7_sound_buzzer_alarm_clock, null),
    EXTRA_DINGING("extra_dinging", R.raw.g7_extra_dinging, R.string.dexcom_g7_sound_dinging, null),
    EXTRA_DOOR_BELL("extra_door_bell", R.raw.g7_extra_door_bell, R.string.dexcom_g7_sound_door_bell, null),
    EXTRA_NERD_ALERT("extra_nerd_alert", R.raw.g7_extra_nerd_alert, R.string.dexcom_g7_sound_nerd_alert, null),
    EXTRA_POLICE_SIREN("extra_police_siren", R.raw.g7_extra_police_siren, R.string.dexcom_g7_sound_police_siren, null),
    EXTRA_SHORT_BEEPS("extra_short_beeps", R.raw.g7_extra_short_beeps, R.string.dexcom_g7_sound_short_beeps, null),
    EXTRA_SONAR_HORN("extra_sonar_horn", R.raw.g7_extra_sonar_horn, R.string.dexcom_g7_sound_sonar_horn, null),
    EXTRA_TACATACA("extra_tacataca", R.raw.g7_extra_tacataca, R.string.dexcom_g7_sound_tacataca, null),
    EXTRA_TRUCK_SIREN("extra_truck_siren", R.raw.g7_extra_truck_siren, R.string.dexcom_g7_sound_truck_siren, null),
    EXTRA_UH_OH("extra_uh_oh", R.raw.g7_extra_uh_oh, R.string.dexcom_g7_sound_uh_oh, null);

    companion object {

        fun byId(id: String): G7AlarmSound? = entries.firstOrNull { it.id == id }

        /** The sounds offered for one alarm: its own, then the extras. */
        fun choicesFor(family: G7SoundFamily): List<G7AlarmSound> =
            entries.filter { it.family == family } + entries.filter { it.family == null }
    }
}
