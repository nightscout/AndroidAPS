package app.aaps.ios.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import platform.UIKit.UIApplication

/**
 * Holds the screen awake while the app is in front, when the user asked for it.
 *
 * `idleTimerDisabled` is the iOS counterpart of the `FLAG_KEEP_SCREEN_ON` window flag that
 * `ComposeMainActivity` sets, and it reaches exactly as far: both hold only while the app is the one
 * on screen. iOS puts the normal auto-lock timer back when the app goes to the background, and
 * neither platform can stop the user locking the phone by hand, so this cannot leave a phone awake
 * in a pocket.
 *
 * It needs no entitlement and asks the user for nothing.
 *
 * The flag is application wide rather than per view, so [onDispose] clears it: a composition that
 * goes away must not leave the timer switched off behind it.
 */
@Composable
fun KeepScreenOnEffect(preferences: Preferences) {
    // `observe` answers from the stored value through the `BooleanNonPreferenceKey` overload of
    // `get`, which falls back to `key.defaultValue` and so never applies `calculatedDefaultValue` -
    // the one that makes this true on a client. Observing is therefore only a change signal here,
    // and the value itself is read with the `BooleanPreferenceKey` overload, which does apply it.
    // `ComposeMainActivity.setupWakeLock` splits it the same way for the same reason.
    val changed by preferences.observe(BooleanKey.OverviewKeepScreenOn).collectAsState()
    val keepScreenOn = remember(changed) { preferences.get(BooleanKey.OverviewKeepScreenOn) }

    DisposableEffect(keepScreenOn) {
        UIApplication.sharedApplication.idleTimerDisabled = keepScreenOn
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
