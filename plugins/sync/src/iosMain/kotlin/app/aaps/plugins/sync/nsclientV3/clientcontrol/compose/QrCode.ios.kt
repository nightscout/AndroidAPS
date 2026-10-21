package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * No QR code on iOS yet. iOS runs only as a client, and only the master shows a code, so the screens that would show
 * one are never reached there; the code options are hidden when this is null.
 */
internal actual fun qrModules(text: String): QrModules? = null

/**
 * Not built yet: an iOS client pairs with the PIN. The scanner still has to be written for iOS (AVFoundation can read
 * QR codes without a library); until then the scan buttons are hidden on iOS.
 */
internal actual val qrScannerAvailable: Boolean = false

@Composable
internal actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    Box(modifier)
}
