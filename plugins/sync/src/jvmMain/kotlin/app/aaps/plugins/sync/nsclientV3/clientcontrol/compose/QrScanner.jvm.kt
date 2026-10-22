package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A desktop has no camera to point at the master: it pairs with the PIN, so the scan buttons are hidden here. */
internal actual val qrScannerAvailable: Boolean = false

@Composable
internal actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    Box(modifier)
}
