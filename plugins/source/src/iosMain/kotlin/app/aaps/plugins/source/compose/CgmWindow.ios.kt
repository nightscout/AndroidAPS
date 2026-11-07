package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

/**
 * Taken from the window rather than the device, which is the closest iOS equivalent. An iPad in
 * split view therefore reports the pane it actually has, which is the number a layout wants anyway.
 */
@Composable
actual fun cgmWindowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}
