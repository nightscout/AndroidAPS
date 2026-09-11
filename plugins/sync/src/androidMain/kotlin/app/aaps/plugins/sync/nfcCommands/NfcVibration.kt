package app.aaps.plugins.sync.nfcCommands

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Short buzz for a command chain that worked, two shorter ones for a chain that did not.
 *
 * A tag is often scanned without looking at the phone, so this is the only feedback the user gets at
 * the moment of the scan. It lives here, called from the screens, rather than on `NfcCommandsPlugin`:
 * the vibrator needs a [Context], and this was the last thing making the plugin ask for one.
 *
 * Failures are swallowed. A missing or busy vibrator must not turn into a failed command, and the
 * message on screen carries the real outcome anyway.
 */
internal fun vibrateForNfcResult(context: Context, success: Boolean) {
    runCatching {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager ?: return
        val effect = if (success) {
            VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1)
        }
        manager.defaultVibrator.vibrate(effect)
    }
}
