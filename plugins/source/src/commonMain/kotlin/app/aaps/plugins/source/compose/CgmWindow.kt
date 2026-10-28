package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.TABLET_MIN_SW_DP

/** How much width the screen has to work with. */
enum class CgmWidth {

    /** Phone in portrait. One column, compact controls. */
    Compact,

    /** Small tablet, or a phone in landscape on a wide device. One centred column. */
    Medium,

    /** Tablet or desktop-sized window. Two panes side by side. */
    Expanded,
}

/**
 * Size of the window a ONE+ screen is drawn in.
 *
 * The width classes follow the same 600 dp step as [TABLET_MIN_SW_DP], which the theme already uses
 * to scale typography, so a screen never disagrees with the theme about what a tablet is.
 *
 * [isShort] is the case the original screens missed: a phone in landscape has plenty of width but
 * only about 360 dp of height, so anything tall (the warm-up ring, a stack of full width buttons)
 * has to be laid out sideways instead.
 */
@Immutable
data class CgmWindow(
    val width: CgmWidth,
    val isShort: Boolean,
) {

    /** True when there is enough width to show two panes next to each other. */
    val isTwoPane: Boolean get() = width == CgmWidth.Expanded
}

/** Width in dp at which a screen may split into two panes. */
private const val EXPANDED_MIN_WIDTH_DP = 840

/** Height in dp below which a screen is treated as short (phone in landscape). */
private const val SHORT_MAX_HEIGHT_DP = 480

/**
 * Current width and height in dp of the window the screen is drawn in.
 *
 * Platform specific for the same reason as `smallestScreenWidthDp` in `:core:ui`: Android answers
 * from the device configuration, which is what these screens have always read, and taking the
 * numbers from anywhere else would change how they lay out. The other platforms have no such
 * configuration, so there the window itself is the answer.
 */
@Composable
expect fun cgmWindowSizeDp(): DpSize

@Composable
fun rememberCgmWindow(): CgmWindow {
    val size = cgmWindowSizeDp()
    return remember(size) {
        CgmWindow(
            width = when {
                size.width >= EXPANDED_MIN_WIDTH_DP.dp -> CgmWidth.Expanded
                size.width >= TABLET_MIN_SW_DP.dp      -> CgmWidth.Medium
                else                                   -> CgmWidth.Compact
            },
            isShort = size.height < SHORT_MAX_HEIGHT_DP.dp,
        )
    }
}
