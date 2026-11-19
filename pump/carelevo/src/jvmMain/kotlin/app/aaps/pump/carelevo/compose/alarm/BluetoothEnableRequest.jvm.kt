package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.runtime.Composable

/** A desktop app cannot ask to turn Bluetooth on. The desktop app links no pump driver; this only lets the module build. */
@Composable
internal actual fun rememberBluetoothEnableRequest(onEnabled: () -> Unit): (() -> Unit)? = null
