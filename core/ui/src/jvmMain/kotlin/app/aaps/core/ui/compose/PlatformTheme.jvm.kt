package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import app.aaps.core.interfaces.utils.usesTwelveHourClock
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
 * Read from the JVM's short time format for the default locale, which follows the machine's regional
 * settings - the closest desktop has to Android's separate 24-hour switch.
 *
 * The pattern is read with the shared [usesTwelveHourClock], so desktop cannot repeat the AM/PM
 * mistake that helper documents.
 */
@Composable
actual fun is24HourClock(): Boolean {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()
    return usesTwelveHourClock(pattern.orEmpty()) != true
}

/**
 * See the expect declaration: a desktop window is resized by the user, not rotated, so there is no
 * orientation to hold and this does nothing.
 */
@Composable
actual fun LockPortraitOrientation() = Unit
