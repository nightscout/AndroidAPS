package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Composable

/**
 * Keeps the screen on while it is shown: a wizard step waits for the pump, and a screen that turns off in the middle of a
 * reservoir change leaves the user without the next step.
 */
@Composable
internal expect fun KeepScreenOn()
