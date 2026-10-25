package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

/**
 * No QR code is drawn on iOS. iOS runs only as a client, and only the master shows a code, so the
 * screens that would draw one are never reached there; the code options are hidden when this is
 * null. Reading a code is a different matter and is implemented - see `QrScanner.ios.kt`.
 */
internal actual fun qrModules(text: String): QrModules? = null
