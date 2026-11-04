package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Empty on purpose. The desktop client pairs with the PIN, not with the camera.
 *
 * This is **not** an unfinished scanner, so please do not "finish" it. It exists because the
 * `expect` declaration needs an `actual` for the JVM target to compile at all, and nothing ever
 * draws it: [app.aaps.core.ui.compose.qrScannerAvailable] is false on JVM, and both ways into the
 * scanner are gated on it - the set-up wizard step (`SWDefinition`) and the "scan instead" button on
 * the PIN screen (`PairWithMasterScreen`). Relax either gate and the user gets this blank box with
 * no explanation, which is the one outcome to avoid.
 *
 * Why there is no desktop camera (looked at 2026-10-22): Java has no camera API, so every option is
 * a third party native bundle. The realistic one is JavaCV with OpenCV's videoio, about 26 MB of
 * natives per platform - affordable, but it buys convenience rather than a missing capability, since
 * the PIN already pairs a desktop client. It also cannot offer a camera picker outside Windows,
 * because OpenCV only enumerates cameras there, and the camera permission of a packaged macOS app is
 * an unproven risk of the same kind that terminated the iOS app when its usage description was
 * missing. If this is ever revisited, decoding is the easy half and already paid for: ZXing is on the
 * desktop classpath for the master's QR code, so only the frames are missing.
 */
@Composable
internal actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
    Box(modifier)
}
