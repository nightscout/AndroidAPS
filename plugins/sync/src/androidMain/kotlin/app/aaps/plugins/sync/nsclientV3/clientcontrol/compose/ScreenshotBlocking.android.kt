package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * FLAG_SECURE for as long as the caller is composed.
 *
 * Cleared on dispose, so the flag does not leak to the rest of the app after the PIN is gone.
 *
 * [LocalActivity] rather than casting [androidx.compose.ui.platform.LocalContext]: the context in a
 * composition is often a `ContextWrapper`, and `as? Activity` on one of those is null even though an
 * activity is right there. That would leave the window unflagged while a PIN is on screen - the flag
 * silently not applied, which is the failure that matters here.
 */
@Composable
actual fun blockScreenshotsWhileVisible(): Boolean {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // False when the composable is not hosted by an Activity - then nothing was set, and saying
    // "protected" would be a lie.
    return activity != null
}
