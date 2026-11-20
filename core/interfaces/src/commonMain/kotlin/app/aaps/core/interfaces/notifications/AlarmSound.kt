package app.aaps.core.interfaces.notifications

/**
 * One of the alarm sounds AAPS ships.
 *
 * These used to travel as bare `R.raw.*` ints. An id is Android only, so it kept every declaring
 * file out of common code, and it carried two further problems that this type removes:
 *
 * - **Two different "no sound".** `runAlarm` took `soundId: Int = 0` while `AapsNotification` took a
 *   nullable id, and the player guarded with `if (soundRes == 0) return` in two places. Absence is
 *   now spelled `null`, once, and the compiler enforces it.
 * - **An id in an Intent.** The chosen sound is passed to the full screen alarm activity as an
 *   extra, and a resource id is a build specific number - the same value means something else in
 *   the next build. The stable [name] travels instead.
 *
 * Deliberately a closed set: the sound files live in `:core:ui/res/raw`. Adding one means adding an
 * entry here, which makes the `when` in the Android resolver stop compiling until it is handled.
 *
 * The `TRIO_*` sounds come from Trio (MIT licence, see `core/ui/SOUNDS.md`). Only the CGM alarms
 * offer them, as the sound the user chose for one alarm. The iOS build copies every file named
 * here into the app bundle, so [fileName] and [fileExtension] must match the file in `res/raw`.
 *
 * @param fileName the file name without extension, the same on Android and iOS.
 * @param fileExtension the file type, `mp3` for the AAPS sounds, `m4a` for the Trio sounds.
 * @param trioSet the Trio alarm this sound was made for, null for an AAPS sound or a Trio tone that
 *   any alarm may use.
 */
enum class AlarmSound(
    val fileName: String,
    val fileExtension: String = "mp3",
    val trioSet: TrioSoundSet? = null,
) {

    /** Standard alarm. */
    ALARM("alarm"),

    /** Urgent alarm - used for the Nightscout urgent announcement path. */
    URGENT_ALARM("urgentalarm"),

    /** General error. */
    ERROR("error"),

    /** Bolus delivery failure. */
    BOLUS_ERROR("boluserror"),

    /** The "time to eat" timer. A soft kitchen timer, so it is not taken for a warning. */
    EAT_REMINDER("eatreminder"),

    // Trio sounds: style (acoustic, electronic), the alarm they were made for, urgency level.
    TRIO_ACOUSTIC_URGENT_LOW_MILD("trio_acoustic_ulg_mild", "m4a", TrioSoundSet.URGENT_LOW),
    TRIO_ACOUSTIC_URGENT_LOW_MEDIUM("trio_acoustic_ulg_medium", "m4a", TrioSoundSet.URGENT_LOW),
    TRIO_ACOUSTIC_URGENT_LOW_HARSH("trio_acoustic_ulg_harsh", "m4a", TrioSoundSet.URGENT_LOW),
    TRIO_ACOUSTIC_LOW_MILD("trio_acoustic_lg_mild", "m4a", TrioSoundSet.LOW),
    TRIO_ACOUSTIC_LOW_MEDIUM("trio_acoustic_lg_medium", "m4a", TrioSoundSet.LOW),
    TRIO_ACOUSTIC_LOW_HARSH("trio_acoustic_lg_harsh", "m4a", TrioSoundSet.LOW),
    TRIO_ACOUSTIC_LOW_SOON_MILD("trio_acoustic_lgs_mild", "m4a", TrioSoundSet.LOW_SOON),
    TRIO_ACOUSTIC_LOW_SOON_MEDIUM("trio_acoustic_lgs_medium", "m4a", TrioSoundSet.LOW_SOON),
    TRIO_ACOUSTIC_LOW_SOON_HARSH("trio_acoustic_lgs_harsh", "m4a", TrioSoundSet.LOW_SOON),
    TRIO_ACOUSTIC_HIGH_MILD("trio_acoustic_hg_mild", "m4a", TrioSoundSet.HIGH),
    TRIO_ACOUSTIC_HIGH_MEDIUM("trio_acoustic_hg_medium", "m4a", TrioSoundSet.HIGH),
    TRIO_ACOUSTIC_HIGH_HARSH("trio_acoustic_hg_harsh", "m4a", TrioSoundSet.HIGH),
    TRIO_ACOUSTIC_DEVICE_CRITICAL_MILD("trio_acoustic_dcr_mild", "m4a", TrioSoundSet.DEVICE_CRITICAL),
    TRIO_ACOUSTIC_DEVICE_CRITICAL_MEDIUM("trio_acoustic_dcr_medium", "m4a", TrioSoundSet.DEVICE_CRITICAL),
    TRIO_ACOUSTIC_DEVICE_CRITICAL_HARSH("trio_acoustic_dcr_harsh", "m4a", TrioSoundSet.DEVICE_CRITICAL),
    TRIO_ACOUSTIC_DEVICE_TIME_SENSITIVE_MILD("trio_acoustic_dts_mild", "m4a", TrioSoundSet.DEVICE_TIME_SENSITIVE),
    TRIO_ACOUSTIC_DEVICE_TIME_SENSITIVE_MEDIUM("trio_acoustic_dts_medium", "m4a", TrioSoundSet.DEVICE_TIME_SENSITIVE),
    TRIO_ACOUSTIC_DEVICE_TIME_SENSITIVE_HARSH("trio_acoustic_dts_harsh", "m4a", TrioSoundSet.DEVICE_TIME_SENSITIVE),
    TRIO_ELECTRONIC_URGENT_LOW_MILD("trio_electronic_ulg_mild", "m4a", TrioSoundSet.URGENT_LOW),
    TRIO_ELECTRONIC_URGENT_LOW_MEDIUM("trio_electronic_ulg_medium", "m4a", TrioSoundSet.URGENT_LOW),
    TRIO_ELECTRONIC_URGENT_LOW_HARSH("trio_electronic_ulg_harsh", "m4a", TrioSoundSet.URGENT_LOW),
    TRIO_ELECTRONIC_LOW_MILD("trio_electronic_lg_mild", "m4a", TrioSoundSet.LOW),
    TRIO_ELECTRONIC_LOW_MEDIUM("trio_electronic_lg_medium", "m4a", TrioSoundSet.LOW),
    TRIO_ELECTRONIC_LOW_HARSH("trio_electronic_lg_harsh", "m4a", TrioSoundSet.LOW),
    TRIO_ELECTRONIC_LOW_SOON_MILD("trio_electronic_lgs_mild", "m4a", TrioSoundSet.LOW_SOON),
    TRIO_ELECTRONIC_LOW_SOON_MEDIUM("trio_electronic_lgs_medium", "m4a", TrioSoundSet.LOW_SOON),
    TRIO_ELECTRONIC_LOW_SOON_HARSH("trio_electronic_lgs_harsh", "m4a", TrioSoundSet.LOW_SOON),
    TRIO_ELECTRONIC_HIGH_MILD("trio_electronic_hg_mild", "m4a", TrioSoundSet.HIGH),
    TRIO_ELECTRONIC_HIGH_MEDIUM("trio_electronic_hg_medium", "m4a", TrioSoundSet.HIGH),
    TRIO_ELECTRONIC_HIGH_HARSH("trio_electronic_hg_harsh", "m4a", TrioSoundSet.HIGH),
    TRIO_ELECTRONIC_DEVICE_CRITICAL_MILD("trio_electronic_dcr_mild", "m4a", TrioSoundSet.DEVICE_CRITICAL),
    TRIO_ELECTRONIC_DEVICE_CRITICAL_MEDIUM("trio_electronic_dcr_medium", "m4a", TrioSoundSet.DEVICE_CRITICAL),
    TRIO_ELECTRONIC_DEVICE_CRITICAL_HARSH("trio_electronic_dcr_harsh", "m4a", TrioSoundSet.DEVICE_CRITICAL),
    TRIO_ELECTRONIC_DEVICE_TIME_SENSITIVE_MILD("trio_electronic_dts_mild", "m4a", TrioSoundSet.DEVICE_TIME_SENSITIVE),
    TRIO_ELECTRONIC_DEVICE_TIME_SENSITIVE_MEDIUM("trio_electronic_dts_medium", "m4a", TrioSoundSet.DEVICE_TIME_SENSITIVE),
    TRIO_ELECTRONIC_DEVICE_TIME_SENSITIVE_HARSH("trio_electronic_dts_harsh", "m4a", TrioSoundSet.DEVICE_TIME_SENSITIVE),
    TRIO_TONE_GLASS_BELL("trio_tone_glass_bell", "m4a", null),
    TRIO_TONE_HARP("trio_tone_harp", "m4a", null),
    TRIO_TONE_MEOW("trio_tone_meow", "m4a", null),
    TRIO_TONE_SAW_WAVE("trio_tone_saw_wave", "m4a", null),
    TRIO_TONE_HARD_SAW_WAVE("trio_tone_hard_saw_wave", "m4a", null),
    TRIO_TONE_SINE_WAVE("trio_tone_sine_wave", "m4a", null),
    TRIO_TONE_OPEN_CAN("trio_tone_open_can", "m4a", null),
    TRIO_TONE_OPEN_WRAPPER("trio_tone_open_wrapper", "m4a", null);

    /** True for a sound from Trio, see `core/ui/SOUNDS.md`. */
    val isTrio: Boolean get() = fileName.startsWith("trio_")

    companion object {

        /** The Trio tones that any alarm may use. */
        val trioTones: List<AlarmSound> get() = entries.filter { it.isTrio && it.trioSet == null }

        /** The Trio sounds made for [set], mild to harsh, acoustic first. */
        fun trioSoundsFor(set: TrioSoundSet): List<AlarmSound> = entries.filter { it.trioSet == set }
    }
}

/**
 * The Trio alarm a sound set was written for. Several CGM alarms share a set where Trio has none of
 * their own: the rate alarms use the low soon and high sets, and the sensor alarms use the device
 * sets.
 */
enum class TrioSoundSet { URGENT_LOW, LOW, LOW_SOON, HIGH, DEVICE_CRITICAL, DEVICE_TIME_SENSITIVE }
