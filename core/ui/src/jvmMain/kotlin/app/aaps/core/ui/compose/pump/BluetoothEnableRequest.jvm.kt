package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable

/** The desktop app has no Bluetooth and no pump driver. */
@Composable
actual fun rememberBluetoothEnableRequest(onResult: (enabled: Boolean) -> Unit): (() -> Unit)? = null
