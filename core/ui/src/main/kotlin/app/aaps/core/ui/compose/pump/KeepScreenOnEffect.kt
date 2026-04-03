package app.aaps.core.ui.compose.pump

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the screen on and locks orientation while this composable is in the composition.
 * Restores previous state on disposal.
 *
 * Use in pump wizard workflows to prevent screen timeout during activation.
 */
@Composable
fun KeepScreenOnEffect() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }
}
