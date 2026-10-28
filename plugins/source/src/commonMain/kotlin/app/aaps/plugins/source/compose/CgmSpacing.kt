package app.aaps.plugins.source.compose

import androidx.compose.ui.unit.dp

/**
 * Sizes used only by the CGM screens of this module. The shared sizes are in `AapsSpacing`.
 */
internal object CgmSpacing {

    /** Widest column a form or status screen should occupy on a tablet. */
    val readableContentMaxWidth = 560.dp

    /** Shortest height of a tappable list row (sensor scan result, etc.). */
    val listRowMinHeight = 56.dp

    /** Smallest usable CGM warm-up ring. */
    val warmupRingMin = 120.dp

    /** Largest CGM warm-up ring, so it does not swallow a tablet pane. */
    val warmupRingMax = 240.dp
}
