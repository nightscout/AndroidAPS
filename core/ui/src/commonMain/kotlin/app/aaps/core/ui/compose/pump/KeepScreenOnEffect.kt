package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable

/**
 * Keeps the screen on while this composable is in the composition, and puts back what was there before
 * when it leaves.
 *
 * Use it in pump wizards. A phone that locks itself in the middle of an activation leaves the pump
 * session to background Bluetooth, and on iOS the app can be suspended before the step ends.
 */
@Composable
expect fun KeepScreenOnEffect()
