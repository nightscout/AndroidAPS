package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.runtime.Composable

/** iOS has no request an app can make to turn Bluetooth on. The user does it in Control Center or Settings. */
@Composable
internal actual fun rememberBluetoothEnableRequest(onEnabled: () -> Unit): (() -> Unit)? = null
