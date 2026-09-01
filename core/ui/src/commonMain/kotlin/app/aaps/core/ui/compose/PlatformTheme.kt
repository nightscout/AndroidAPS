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

/**
 * Whether the screen is currently wider than it is tall.
 *
 * Platform specific for the same reason as [smallestScreenWidthDp]: Android answers from the device
 * configuration, which is what the overview layout has always used, and reading it any other way
 * would change how the screen behaves in split view.
 */
@Composable
expect fun isLandscape(): Boolean

/**
 * Whether the user wants a 24 hour clock.
 *
 * A user setting, not a locale one: both platforms let it be changed independently of the region,
 * and a time picker that ignores it is immediately wrong to the person reading it.
 */
@Composable
expect fun is24HourClock(): Boolean

/**
 * Holds the screen in portrait for as long as the calling composable is shown, then restores
 * whatever the orientation was before.
 *
 * A body-map screen is drawn to a fixed portrait layout, so rotating it produces a picture that no
 * longer lines up. Platform specific because only Android lets a screen ask for this: on iOS the
 * supported orientations belong to the app delegate, not to a view, so there it does nothing and the
 * screen can rotate. That is a cosmetic difference, not a loss of function.
 */
@Composable
expect fun LockPortraitOrientation()
