package app.aaps.pump.carelevo.compose.alarm

import androidx.compose.runtime.Composable

/**
 * How this platform asks the user to turn Bluetooth on: call the returned function to ask. [onEnabled] runs when
 * the user said yes. Null where an app cannot ask, as on iOS; the caller then tells the user in text instead.
 */
@Composable
internal expect fun rememberBluetoothEnableRequest(onEnabled: () -> Unit): (() -> Unit)?
