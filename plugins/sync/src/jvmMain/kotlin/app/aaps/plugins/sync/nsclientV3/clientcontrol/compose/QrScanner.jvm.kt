package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    Box(modifier)
}
