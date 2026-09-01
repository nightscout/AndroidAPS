package app.aaps.core.ui.compose

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterNoStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.min

/** iOS styles its status bar through the view controller, not from inside the composition. */
@Composable
actual fun SystemBarAppearance(isDark: Boolean) = Unit

/**
 * Taken from the window rather than the device, which is the closest iOS equivalent. An iPad in
 * split view therefore reports the pane it actually has, which is the number a layout wants anyway.
 */
@Composable
actual fun smallestScreenWidthDp(): Int {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { min(size.width, size.height).toDp().value.toInt() }
}

/** No device orientation to consult, so the window's own shape is the answer. */
@Composable
actual fun isLandscape(): Boolean =
    LocalWindowInfo.current.containerSize.let { it.width > it.height }

/**
 * iOS exposes no flag for this, so ask a short time formatter what pattern the user's settings
 * produce and look for the AM/PM symbol in it. Its absence is the 24 hour clock. This follows the
 * "24-Hour Time" switch in Settings, because that switch is what rewrites the pattern.
 */
@Composable
actual fun is24HourClock(): Boolean {
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.dateFormat?.contains("a") != true
}
