package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The device configuration, which is what these screens have always read. */
@Composable
actual fun cgmWindowSizeDp(): DpSize {
    val configuration = LocalConfiguration.current
    return DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
}
