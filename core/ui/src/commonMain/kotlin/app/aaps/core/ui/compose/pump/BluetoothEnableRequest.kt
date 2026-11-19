package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable

/**
 * How this platform asks the user to turn Bluetooth on: call the returned function to ask. [onResult] gets true when
 * the user turned it on. Null where an app cannot ask, as on iOS (the user turns it on in the system settings); the
 * caller then tells the user in text instead. Used by `BlePreCheckHost` for every pump, and by a driver that asks
 * from somewhere else (Carelevo, when its Bluetooth-off alarm is cleared).
 */
@Composable
expect fun rememberBluetoothEnableRequest(onResult: (enabled: Boolean) -> Unit): (() -> Unit)?
