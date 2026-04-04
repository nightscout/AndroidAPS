package app.aaps.core.interfaces.overview.graph

import kotlinx.coroutines.flow.StateFlow

/**
 * Series types available for secondary graphs.
 * Each type maps to a data flow in OverviewDataCache.
 * IOB implicitly includes bolus markers, COB implicitly includes carbs markers.
 */
enum class SeriesType {
    IOB,
    ABS_IOB,
    COB,
    BGI,
    DEVIATIONS,
    SENSITIVITY,
    VAR_SENSITIVITY,
    DEV_SLOPE,
    HEART_RATE,
    STEPS
}

/**
 * Toggleable overlays on the primary BG graph.
 * Fixed elements (BG readings, predictions, target line, treatment belt, EPS) are not listed here.
 */
enum class BgOverlay {
    BASAL,
    ACTIVITY
}

/**
 * Configuration for the overview graphs.
 *
 * @param bgOverlays  Which overlays are enabled on the primary BG graph (default: both)
 * @param secondaryGraphs  Ordered list of secondary graph configurations (default: IOB, COB)
 */
data class GraphConfig(
    val bgOverlays: Set<BgOverlay> = setOf(BgOverlay.BASAL, BgOverlay.ACTIVITY),
    val secondaryGraphs: List<Set<SeriesType>> = listOf(
        setOf(SeriesType.IOB),
        setOf(SeriesType.COB)
    )
) {

    companion object {

        /** Maximum number of secondary graphs allowed */
        const val MAX_SECONDARY_GRAPHS = 5
    }
}

/**
 * Repository for graph configuration persistence.
 * Loads/saves GraphConfig and exposes it as a reactive StateFlow.
 */
interface GraphConfigRepository {

    /** Current graph configuration, updated reactively on changes */
    val graphConfigFlow: StateFlow<GraphConfig>

    /** Update and persist graph configuration */
    fun update(config: GraphConfig)
}
