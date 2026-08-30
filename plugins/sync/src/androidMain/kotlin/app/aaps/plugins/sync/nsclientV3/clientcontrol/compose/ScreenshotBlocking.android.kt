package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * FLAG_SECURE for as long as the caller is composed.
 *
 * Cleared on dispose, so the flag does not leak to the rest of the app after the PIN is gone.
 */
@Composable
actual fun blockScreenshotsWhileVisible(): Boolean {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // False when the composable is not hosted by an Activity - then nothing was set, and saying
    // "protected" would be a lie.
    return activity != null
}
