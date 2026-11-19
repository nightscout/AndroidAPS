package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable

/** iOS lets no app turn Bluetooth on or show a dialog for it: the user does it in the system settings. */
@Composable
actual fun rememberBluetoothEnableRequest(onResult: (enabled: Boolean) -> Unit): (() -> Unit)? = null
