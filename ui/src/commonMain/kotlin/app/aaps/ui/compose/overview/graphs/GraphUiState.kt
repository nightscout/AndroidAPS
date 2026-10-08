package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.overview.graph.BgInfoData

/**
 * Static chart configuration, which does not change during the graph's lifetime.
 *
 * This and [BgInfoUiState] used to sit next to `GraphViewModel` in its file. Neither has any Android
 * in it, but the view model beside them is Android bound, and a file moves as a whole - so the
 * composables and previews reading these were pinned to androidMain by proximity rather than by a
 * real dependency.
 */
data class ChartConfig(
    val highMark: Double,
    val lowMark: Double
)

/** What the BG info section shows. */
@Immutable
data class BgInfoUiState(
    val bgInfo: BgInfoData?,
    val timeAgoText: String
)
