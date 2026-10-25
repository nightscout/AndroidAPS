package app.aaps.cgm.dexcomg7.alarm

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import app.aaps.cgm.dexcomg7.R

/**
 * Which alarm a sound set was written for. Several of our alarms share a set where Trio has none of
 * their own: the rate alarms use the low soon and high sets, and every sensor alarm but "failed" uses
 * the time-sensitive device set.
 */
enum class G7SoundSet {

    URGENT_LOW, LOW, LOW_SOON, HIGH, DEVICE_CRITICAL, DEVICE_TIME_SENSITIVE
}

/** The two styles each set comes in. */
enum class G7SoundTheme(@StringRes val label: Int) {

    ACOUSTIC(R.string.dexcom_g7_sound_acoustic),
    ELECTRONIC(R.string.dexcom_g7_sound_electronic)
}

/**
 * How urgent a sound is. Each level is its own recording, with its own sound, loudness and length,
 * not the same recording played louder.
 */
enum class G7SoundLevel(@StringRes val label: Int) {

    MILD(R.string.dexcom_g7_sound_mild),
    MEDIUM(R.string.dexcom_g7_sound_medium),
    HARSH(R.string.dexcom_g7_sound_harsh)
}

/**
 * The alarm sounds. They were made for Trio and come from nightscout/Trio pull request 1645 (MIT
 * licence), see `cgm/dexcomg7/SOUNDS.md`. Each [G7SoundSet] comes in both [G7SoundTheme]s at three
 * [G7SoundLevel]s, and there are some extra tones that any alarm may use.
 *
 * [id] is what the settings store, so it must not change once released.
 */
enum class G7AlarmSound(
    @RawRes val rawRes: Int,
    @StringRes val label: Int,
    /** Null for an extra tone, which any alarm may use. */
    val set: G7SoundSet? = null,
    val theme: G7SoundTheme? = null,
    val level: G7SoundLevel? = null
) {

    ACOUSTIC_URGENT_LOW_MILD(R.raw.g7_acoustic_ulg_mild, R.string.dexcom_g7_sound_acoustic_mild, G7SoundSet.URGENT_LOW, G7SoundTheme.ACOUSTIC, G7SoundLevel.MILD),
    ACOUSTIC_URGENT_LOW_MEDIUM(R.raw.g7_acoustic_ulg_medium, R.string.dexcom_g7_sound_acoustic_medium, G7SoundSet.URGENT_LOW, G7SoundTheme.ACOUSTIC, G7SoundLevel.MEDIUM),
    ACOUSTIC_URGENT_LOW_HARSH(R.raw.g7_acoustic_ulg_harsh, R.string.dexcom_g7_sound_acoustic_harsh, G7SoundSet.URGENT_LOW, G7SoundTheme.ACOUSTIC, G7SoundLevel.HARSH),
    ACOUSTIC_LOW_MILD(R.raw.g7_acoustic_lg_mild, R.string.dexcom_g7_sound_acoustic_mild, G7SoundSet.LOW, G7SoundTheme.ACOUSTIC, G7SoundLevel.MILD),
    ACOUSTIC_LOW_MEDIUM(R.raw.g7_acoustic_lg_medium, R.string.dexcom_g7_sound_acoustic_medium, G7SoundSet.LOW, G7SoundTheme.ACOUSTIC, G7SoundLevel.MEDIUM),
    ACOUSTIC_LOW_HARSH(R.raw.g7_acoustic_lg_harsh, R.string.dexcom_g7_sound_acoustic_harsh, G7SoundSet.LOW, G7SoundTheme.ACOUSTIC, G7SoundLevel.HARSH),
    ACOUSTIC_LOW_SOON_MILD(R.raw.g7_acoustic_lgs_mild, R.string.dexcom_g7_sound_acoustic_mild, G7SoundSet.LOW_SOON, G7SoundTheme.ACOUSTIC, G7SoundLevel.MILD),
    ACOUSTIC_LOW_SOON_MEDIUM(R.raw.g7_acoustic_lgs_medium, R.string.dexcom_g7_sound_acoustic_medium, G7SoundSet.LOW_SOON, G7SoundTheme.ACOUSTIC, G7SoundLevel.MEDIUM),
    ACOUSTIC_LOW_SOON_HARSH(R.raw.g7_acoustic_lgs_harsh, R.string.dexcom_g7_sound_acoustic_harsh, G7SoundSet.LOW_SOON, G7SoundTheme.ACOUSTIC, G7SoundLevel.HARSH),
    ACOUSTIC_HIGH_MILD(R.raw.g7_acoustic_hg_mild, R.string.dexcom_g7_sound_acoustic_mild, G7SoundSet.HIGH, G7SoundTheme.ACOUSTIC, G7SoundLevel.MILD),
    ACOUSTIC_HIGH_MEDIUM(R.raw.g7_acoustic_hg_medium, R.string.dexcom_g7_sound_acoustic_medium, G7SoundSet.HIGH, G7SoundTheme.ACOUSTIC, G7SoundLevel.MEDIUM),
    ACOUSTIC_HIGH_HARSH(R.raw.g7_acoustic_hg_harsh, R.string.dexcom_g7_sound_acoustic_harsh, G7SoundSet.HIGH, G7SoundTheme.ACOUSTIC, G7SoundLevel.HARSH),
    ACOUSTIC_DEVICE_CRITICAL_MILD(R.raw.g7_acoustic_dcr_mild, R.string.dexcom_g7_sound_acoustic_mild, G7SoundSet.DEVICE_CRITICAL, G7SoundTheme.ACOUSTIC, G7SoundLevel.MILD),
    ACOUSTIC_DEVICE_CRITICAL_MEDIUM(R.raw.g7_acoustic_dcr_medium, R.string.dexcom_g7_sound_acoustic_medium, G7SoundSet.DEVICE_CRITICAL, G7SoundTheme.ACOUSTIC, G7SoundLevel.MEDIUM),
    ACOUSTIC_DEVICE_CRITICAL_HARSH(R.raw.g7_acoustic_dcr_harsh, R.string.dexcom_g7_sound_acoustic_harsh, G7SoundSet.DEVICE_CRITICAL, G7SoundTheme.ACOUSTIC, G7SoundLevel.HARSH),
    ACOUSTIC_DEVICE_TIME_SENSITIVE_MILD(R.raw.g7_acoustic_dts_mild, R.string.dexcom_g7_sound_acoustic_mild, G7SoundSet.DEVICE_TIME_SENSITIVE, G7SoundTheme.ACOUSTIC, G7SoundLevel.MILD),
    ACOUSTIC_DEVICE_TIME_SENSITIVE_MEDIUM(R.raw.g7_acoustic_dts_medium, R.string.dexcom_g7_sound_acoustic_medium, G7SoundSet.DEVICE_TIME_SENSITIVE, G7SoundTheme.ACOUSTIC, G7SoundLevel.MEDIUM),
    ACOUSTIC_DEVICE_TIME_SENSITIVE_HARSH(R.raw.g7_acoustic_dts_harsh, R.string.dexcom_g7_sound_acoustic_harsh, G7SoundSet.DEVICE_TIME_SENSITIVE, G7SoundTheme.ACOUSTIC, G7SoundLevel.HARSH),

    ELECTRONIC_URGENT_LOW_MILD(R.raw.g7_electronic_ulg_mild, R.string.dexcom_g7_sound_electronic_mild, G7SoundSet.URGENT_LOW, G7SoundTheme.ELECTRONIC, G7SoundLevel.MILD),
    ELECTRONIC_URGENT_LOW_MEDIUM(R.raw.g7_electronic_ulg_medium, R.string.dexcom_g7_sound_electronic_medium, G7SoundSet.URGENT_LOW, G7SoundTheme.ELECTRONIC, G7SoundLevel.MEDIUM),
    ELECTRONIC_URGENT_LOW_HARSH(R.raw.g7_electronic_ulg_harsh, R.string.dexcom_g7_sound_electronic_harsh, G7SoundSet.URGENT_LOW, G7SoundTheme.ELECTRONIC, G7SoundLevel.HARSH),
    ELECTRONIC_LOW_MILD(R.raw.g7_electronic_lg_mild, R.string.dexcom_g7_sound_electronic_mild, G7SoundSet.LOW, G7SoundTheme.ELECTRONIC, G7SoundLevel.MILD),
    ELECTRONIC_LOW_MEDIUM(R.raw.g7_electronic_lg_medium, R.string.dexcom_g7_sound_electronic_medium, G7SoundSet.LOW, G7SoundTheme.ELECTRONIC, G7SoundLevel.MEDIUM),
    ELECTRONIC_LOW_HARSH(R.raw.g7_electronic_lg_harsh, R.string.dexcom_g7_sound_electronic_harsh, G7SoundSet.LOW, G7SoundTheme.ELECTRONIC, G7SoundLevel.HARSH),
    ELECTRONIC_LOW_SOON_MILD(R.raw.g7_electronic_lgs_mild, R.string.dexcom_g7_sound_electronic_mild, G7SoundSet.LOW_SOON, G7SoundTheme.ELECTRONIC, G7SoundLevel.MILD),
    ELECTRONIC_LOW_SOON_MEDIUM(R.raw.g7_electronic_lgs_medium, R.string.dexcom_g7_sound_electronic_medium, G7SoundSet.LOW_SOON, G7SoundTheme.ELECTRONIC, G7SoundLevel.MEDIUM),
    ELECTRONIC_LOW_SOON_HARSH(R.raw.g7_electronic_lgs_harsh, R.string.dexcom_g7_sound_electronic_harsh, G7SoundSet.LOW_SOON, G7SoundTheme.ELECTRONIC, G7SoundLevel.HARSH),
    ELECTRONIC_HIGH_MILD(R.raw.g7_electronic_hg_mild, R.string.dexcom_g7_sound_electronic_mild, G7SoundSet.HIGH, G7SoundTheme.ELECTRONIC, G7SoundLevel.MILD),
    ELECTRONIC_HIGH_MEDIUM(R.raw.g7_electronic_hg_medium, R.string.dexcom_g7_sound_electronic_medium, G7SoundSet.HIGH, G7SoundTheme.ELECTRONIC, G7SoundLevel.MEDIUM),
    ELECTRONIC_HIGH_HARSH(R.raw.g7_electronic_hg_harsh, R.string.dexcom_g7_sound_electronic_harsh, G7SoundSet.HIGH, G7SoundTheme.ELECTRONIC, G7SoundLevel.HARSH),
    ELECTRONIC_DEVICE_CRITICAL_MILD(R.raw.g7_electronic_dcr_mild, R.string.dexcom_g7_sound_electronic_mild, G7SoundSet.DEVICE_CRITICAL, G7SoundTheme.ELECTRONIC, G7SoundLevel.MILD),
    ELECTRONIC_DEVICE_CRITICAL_MEDIUM(R.raw.g7_electronic_dcr_medium, R.string.dexcom_g7_sound_electronic_medium, G7SoundSet.DEVICE_CRITICAL, G7SoundTheme.ELECTRONIC, G7SoundLevel.MEDIUM),
    ELECTRONIC_DEVICE_CRITICAL_HARSH(R.raw.g7_electronic_dcr_harsh, R.string.dexcom_g7_sound_electronic_harsh, G7SoundSet.DEVICE_CRITICAL, G7SoundTheme.ELECTRONIC, G7SoundLevel.HARSH),
    ELECTRONIC_DEVICE_TIME_SENSITIVE_MILD(R.raw.g7_electronic_dts_mild, R.string.dexcom_g7_sound_electronic_mild, G7SoundSet.DEVICE_TIME_SENSITIVE, G7SoundTheme.ELECTRONIC, G7SoundLevel.MILD),
    ELECTRONIC_DEVICE_TIME_SENSITIVE_MEDIUM(R.raw.g7_electronic_dts_medium, R.string.dexcom_g7_sound_electronic_medium, G7SoundSet.DEVICE_TIME_SENSITIVE, G7SoundTheme.ELECTRONIC, G7SoundLevel.MEDIUM),
    ELECTRONIC_DEVICE_TIME_SENSITIVE_HARSH(R.raw.g7_electronic_dts_harsh, R.string.dexcom_g7_sound_electronic_harsh, G7SoundSet.DEVICE_TIME_SENSITIVE, G7SoundTheme.ELECTRONIC, G7SoundLevel.HARSH),

    TONE_GLASS_BELL(R.raw.g7_tone_glass_bell, R.string.dexcom_g7_sound_glass_bell),
    TONE_HARP(R.raw.g7_tone_harp, R.string.dexcom_g7_sound_harp),
    TONE_MEOW(R.raw.g7_tone_meow, R.string.dexcom_g7_sound_meow),
    TONE_SAW_WAVE(R.raw.g7_tone_saw_wave, R.string.dexcom_g7_sound_saw_wave),
    TONE_HARD_SAW_WAVE(R.raw.g7_tone_hard_saw_wave, R.string.dexcom_g7_sound_hard_saw_wave),
    TONE_SINE_WAVE(R.raw.g7_tone_sine_wave, R.string.dexcom_g7_sound_sine_wave),
    TONE_OPEN_CAN(R.raw.g7_tone_open_can, R.string.dexcom_g7_sound_open_can),
    TONE_OPEN_WRAPPER(R.raw.g7_tone_open_wrapper, R.string.dexcom_g7_sound_open_wrapper);

    val id: String = name.lowercase()

    companion object {

        val tones: List<G7AlarmSound> = entries.filter { it.set == null }

        fun byId(id: String): G7AlarmSound? = entries.firstOrNull { it.id == id }

        fun of(set: G7SoundSet, theme: G7SoundTheme, level: G7SoundLevel): G7AlarmSound =
            entries.first { it.set == set && it.theme == theme && it.level == level }

        /** The style and level every alarm starts with. */
        val defaultTheme: G7SoundTheme = G7SoundTheme.ELECTRONIC
        val defaultLevel: G7SoundLevel = G7SoundLevel.MILD

        fun defaultFor(set: G7SoundSet): G7AlarmSound = of(set, defaultTheme, defaultLevel)

        /** The sounds offered for one alarm: its own set, then the extra tones. */
        fun choicesFor(set: G7SoundSet): List<G7AlarmSound> = entries.filter { it.set == set } + tones
    }
}
