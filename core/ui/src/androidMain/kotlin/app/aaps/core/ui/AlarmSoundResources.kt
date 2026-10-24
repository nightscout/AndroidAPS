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
        AlarmSound.ALARM        -> R.raw.alarm
        AlarmSound.URGENT_ALARM -> R.raw.urgentalarm
        AlarmSound.ERROR        -> R.raw.error
        AlarmSound.BOLUS_ERROR  -> R.raw.boluserror
    }
