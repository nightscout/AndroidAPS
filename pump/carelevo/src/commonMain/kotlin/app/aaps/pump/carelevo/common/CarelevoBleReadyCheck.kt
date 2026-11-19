package app.aaps.pump.carelevo.common

import androidx.compose.runtime.Composable

/**
 * Checks that Bluetooth can be used before the pairing wizard scans for a patch. When it cannot, it tells the user
 * why (Bluetooth off, permission missing, no Bluetooth), and calls `onFailed` when the user closes that message.
 *
 * Each platform that registers the Carelevo plugin implements it (Android: `AndroidCarelevoBleReadyCheck`).
 */
interface CarelevoBleReadyCheck {

    /** Shown once when the wizard starts. Calls [onReady] once if Bluetooth can be used. */
    @Composable
    fun Check(onReady: () -> Unit, onFailed: () -> Unit)
}
