package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable

/**
 * Nothing to do on the desktop. It drives no pump over Bluetooth, so no pump wizard runs there, and a
 * computer does not suspend an app that is open on screen.
 */
@Composable
actual fun KeepScreenOnEffect() = Unit
