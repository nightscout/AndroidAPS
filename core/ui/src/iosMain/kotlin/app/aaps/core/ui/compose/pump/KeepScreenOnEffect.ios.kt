package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * The only code that sets `UIApplication.idleTimerDisabled`. Use it for every "keep the screen on", so
 * the holders do not switch each other off (see [ScreenAwakeHolds]).
 *
 * The flag holds only while the app is in front: iOS puts the normal auto-lock back when the app goes to
 * the background, so this cannot keep a phone in a pocket awake. Main thread only, as composition is.
 */
object IosScreenAwake {

    private val holds = ScreenAwakeHolds { keepOn -> UIApplication.sharedApplication.idleTimerDisabled = keepOn }

    fun hold() = holds.hold()

    fun release() = holds.release()
}

@Composable
actual fun KeepScreenOnEffect() {
    DisposableEffect(Unit) {
        IosScreenAwake.hold()
        onDispose { IosScreenAwake.release() }
    }
}
