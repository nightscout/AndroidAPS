package app.aaps.core.ui.compose.pump

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Keeps the screen on and locks orientation while this composable is in the composition.
 * Restores previous state on disposal.
 *
 * Use in pump wizard workflows to prevent screen timeout during activation.
 */
@Composable
fun KeepScreenOnEffect() {
    // LocalActivity, not a cast of LocalContext: that context is usually a ContextWrapper, where
    // `as? Activity` is null and the screen would quietly time out mid pump activation.
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        val window = activity?.window
        // The pump screens live in the same single activity as Overview, and its "Keep screen on"
        // setting puts this same flag on this same window. Take back only what this effect added,
        // or leaving a wizard turns that setting off until the user toggles it again.
        val wasKeptOn = window?.attributes?.flags?.let { it and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 } ?: false
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            if (!wasKeptOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }
}
