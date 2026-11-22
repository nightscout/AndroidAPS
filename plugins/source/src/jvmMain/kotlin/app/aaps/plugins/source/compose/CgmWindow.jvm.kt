package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

/**
 * Taken from the window rather than the display, which is the right number on desktop: the layout
 * cares about the space the app actually has, and the user can resize it to anything.
 */
@Composable
actual fun cgmWindowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}
