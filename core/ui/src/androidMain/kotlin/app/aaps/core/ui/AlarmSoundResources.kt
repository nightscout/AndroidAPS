package app.aaps.core.ui

import androidx.annotation.RawRes
import app.aaps.core.interfaces.notifications.AlarmSound

/**
 * The Android resource behind an [AlarmSound].
 *
 * This is the single place that knows sounds are `R.raw` files at all, which is what lets every
 * declaring interface stay free of Android. It lives in `:core:ui` because that is the module owning
 * the audio files; the `when` is exhaustive, so a new [AlarmSound] fails to compile until it is
 * given a file here.
 */
@get:RawRes
val AlarmSound.rawRes: Int
    get() = when (this) {
        AlarmSound.ALARM                                       -> R.raw.alarm
        AlarmSound.URGENT_ALARM                                -> R.raw.urgentalarm
        AlarmSound.ERROR                                       -> R.raw.error
        AlarmSound.BOLUS_ERROR                                 -> R.raw.boluserror
        AlarmSound.EAT_REMINDER                                -> R.raw.eatreminder
        AlarmSound.TRIO_ACOUSTIC_URGENT_LOW_MILD               -> R.raw.trio_acoustic_ulg_mild
        AlarmSound.TRIO_ACOUSTIC_URGENT_LOW_MEDIUM             -> R.raw.trio_acoustic_ulg_medium
        AlarmSound.TRIO_ACOUSTIC_URGENT_LOW_HARSH              -> R.raw.trio_acoustic_ulg_harsh
        AlarmSound.TRIO_ACOUSTIC_LOW_MILD                      -> R.raw.trio_acoustic_lg_mild
        AlarmSound.TRIO_ACOUSTIC_LOW_MEDIUM                    -> R.raw.trio_acoustic_lg_medium
        AlarmSound.TRIO_ACOUSTIC_LOW_HARSH                     -> R.raw.trio_acoustic_lg_harsh
        AlarmSound.TRIO_ACOUSTIC_LOW_SOON_MILD                 -> R.raw.trio_acoustic_lgs_mild
        AlarmSound.TRIO_ACOUSTIC_LOW_SOON_MEDIUM               -> R.raw.trio_acoustic_lgs_medium
        AlarmSound.TRIO_ACOUSTIC_LOW_SOON_HARSH                -> R.raw.trio_acoustic_lgs_harsh
        AlarmSound.TRIO_ACOUSTIC_HIGH_MILD                     -> R.raw.trio_acoustic_hg_mild
        AlarmSound.TRIO_ACOUSTIC_HIGH_MEDIUM                   -> R.raw.trio_acoustic_hg_medium
        AlarmSound.TRIO_ACOUSTIC_HIGH_HARSH                    -> R.raw.trio_acoustic_hg_harsh
        AlarmSound.TRIO_ACOUSTIC_DEVICE_CRITICAL_MILD          -> R.raw.trio_acoustic_dcr_mild
        AlarmSound.TRIO_ACOUSTIC_DEVICE_CRITICAL_MEDIUM        -> R.raw.trio_acoustic_dcr_medium
        AlarmSound.TRIO_ACOUSTIC_DEVICE_CRITICAL_HARSH         -> R.raw.trio_acoustic_dcr_harsh
        AlarmSound.TRIO_ACOUSTIC_DEVICE_TIME_SENSITIVE_MILD    -> R.raw.trio_acoustic_dts_mild
        AlarmSound.TRIO_ACOUSTIC_DEVICE_TIME_SENSITIVE_MEDIUM  -> R.raw.trio_acoustic_dts_medium
        AlarmSound.TRIO_ACOUSTIC_DEVICE_TIME_SENSITIVE_HARSH   -> R.raw.trio_acoustic_dts_harsh
        AlarmSound.TRIO_ELECTRONIC_URGENT_LOW_MILD             -> R.raw.trio_electronic_ulg_mild
        AlarmSound.TRIO_ELECTRONIC_URGENT_LOW_MEDIUM           -> R.raw.trio_electronic_ulg_medium
        AlarmSound.TRIO_ELECTRONIC_URGENT_LOW_HARSH            -> R.raw.trio_electronic_ulg_harsh
        AlarmSound.TRIO_ELECTRONIC_LOW_MILD                    -> R.raw.trio_electronic_lg_mild
        AlarmSound.TRIO_ELECTRONIC_LOW_MEDIUM                  -> R.raw.trio_electronic_lg_medium
        AlarmSound.TRIO_ELECTRONIC_LOW_HARSH                   -> R.raw.trio_electronic_lg_harsh
        AlarmSound.TRIO_ELECTRONIC_LOW_SOON_MILD               -> R.raw.trio_electronic_lgs_mild
        AlarmSound.TRIO_ELECTRONIC_LOW_SOON_MEDIUM             -> R.raw.trio_electronic_lgs_medium
        AlarmSound.TRIO_ELECTRONIC_LOW_SOON_HARSH              -> R.raw.trio_electronic_lgs_harsh
        AlarmSound.TRIO_ELECTRONIC_HIGH_MILD                   -> R.raw.trio_electronic_hg_mild
        AlarmSound.TRIO_ELECTRONIC_HIGH_MEDIUM                 -> R.raw.trio_electronic_hg_medium
        AlarmSound.TRIO_ELECTRONIC_HIGH_HARSH                  -> R.raw.trio_electronic_hg_harsh
        AlarmSound.TRIO_ELECTRONIC_DEVICE_CRITICAL_MILD        -> R.raw.trio_electronic_dcr_mild
        AlarmSound.TRIO_ELECTRONIC_DEVICE_CRITICAL_MEDIUM      -> R.raw.trio_electronic_dcr_medium
        AlarmSound.TRIO_ELECTRONIC_DEVICE_CRITICAL_HARSH       -> R.raw.trio_electronic_dcr_harsh
        AlarmSound.TRIO_ELECTRONIC_DEVICE_TIME_SENSITIVE_MILD  -> R.raw.trio_electronic_dts_mild
        AlarmSound.TRIO_ELECTRONIC_DEVICE_TIME_SENSITIVE_MEDIUM-> R.raw.trio_electronic_dts_medium
        AlarmSound.TRIO_ELECTRONIC_DEVICE_TIME_SENSITIVE_HARSH -> R.raw.trio_electronic_dts_harsh
        AlarmSound.TRIO_TONE_GLASS_BELL                        -> R.raw.trio_tone_glass_bell
        AlarmSound.TRIO_TONE_HARP                              -> R.raw.trio_tone_harp
        AlarmSound.TRIO_TONE_MEOW                              -> R.raw.trio_tone_meow
        AlarmSound.TRIO_TONE_SAW_WAVE                          -> R.raw.trio_tone_saw_wave
        AlarmSound.TRIO_TONE_HARD_SAW_WAVE                     -> R.raw.trio_tone_hard_saw_wave
        AlarmSound.TRIO_TONE_SINE_WAVE                         -> R.raw.trio_tone_sine_wave
        AlarmSound.TRIO_TONE_OPEN_CAN                          -> R.raw.trio_tone_open_can
        AlarmSound.TRIO_TONE_OPEN_WRAPPER                      -> R.raw.trio_tone_open_wrapper
    }
