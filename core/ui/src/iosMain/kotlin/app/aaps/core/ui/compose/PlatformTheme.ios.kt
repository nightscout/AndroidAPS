package app.aaps.core.ui.compose

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
