package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import java.text.DateFormat
import java.text.SimpleDateFormat
import kotlin.math.min

/** A desktop window has no system bars to style. */
@Composable
actual fun SystemBarAppearance(isDark: Boolean) = Unit

/**
 * Taken from the window rather than the display, which is the right number on desktop: the layout
 * cares about the space the app actually has, and the user can resize it to anything.
 */
@Composable
actual fun smallestScreenWidthDp(): Int {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { min(size.width, size.height).toDp().value.toInt() }
}

/** No device orientation on desktop, so the window's own shape is the answer. */
@Composable
actual fun isLandscape(): Boolean =
    LocalWindowInfo.current.containerSize.let { it.width > it.height }

/**
 * Read from the JVM's short time format for the default locale: a pattern containing `H` is the 24
 * hour clock, `h` the 12 hour one. That follows the user's regional settings, which is the closest
 * desktop has to Android's separate 24-hour switch.
 */
@Composable
actual fun is24HourClock(): Boolean {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()
    return pattern?.contains('H') == true
}

/**
 * See the expect declaration: a desktop window is resized by the user, not rotated, so there is no
 * orientation to hold and this does nothing.
 */
@Composable
actual fun LockPortraitOrientation() = Unit
