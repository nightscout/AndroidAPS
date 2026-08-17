package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable

/**
 * Keeps the system bar icons legible against the bar scrims, which use `colorScheme.surface`.
 *
 * Platform specific because the two platforms do not even agree on what a system bar is: Android
 * flips light/dark icon appearance on the window, and this must react without recreating the
 * activity.
 */
@Composable
expect fun SystemBarAppearance(isDark: Boolean)

/**
 * Smallest screen width in dp, used to decide whether this is a tablet and scale typography.
 *
 * Smallest width rather than current width, so the answer does not change when the device is
 * rotated. Platform specific because Android answers from the device configuration rather than from
 * the size of the window the app happens to occupy.
 */
@Composable
expect fun smallestScreenWidthDp(): Int
