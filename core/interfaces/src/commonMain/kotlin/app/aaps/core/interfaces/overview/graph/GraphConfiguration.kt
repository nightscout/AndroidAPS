package app.aaps.core.interfaces.overview.graph

import app.aaps.core.interfaces.overview.graph.GraphConfig.Companion.MAX_GRAPH_HEIGHT_DP
import kotlinx.coroutines.flow.StateFlow

/**
 * Series types available for all graphs (BG primary and secondary).
 * Each type maps to a data flow in OverviewDataCache.
 * IOB implicitly includes bolus markers, COB implicitly includes carbs markers.
 *
 * Not all types are allowed on all graphs — the UI enforces allowed sets per graph type.
 * BG graph: only BASAL, ACTIVITY.
 * Secondary graphs: all types.
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
    STEPS,
    ACTIVITY,
    PREDICTIONS
}

/**
 * Secondary graph entry: series list + per-graph height (dp).
 * Max 2 series per graph. Height is per-graph user-adjustable.
 */
data class SecondaryGraph(
    val series: List<SeriesType>,
    val height: Int = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
)

/**
 * Configuration for the overview graphs.
 *
 * The BG graph is the only fixed graph. It shows basal, boluses, SMBs, carbs and meter values
 * itself, with optional overlays toggled via [bgOverlays]; the treatment belt above it shows the COB
 * failover. Every graph below it is chosen by the user, IOB and COB included, so by default there is none.
 *
 * @param bgOverlays       Overlay toggles for the BG graph ([SeriesType.ACTIVITY], [SeriesType.PREDICTIONS]).
 * @param secondaryGraphs  Ordered list of user-configurable secondary graph configurations.
 *   Each graph is a List (not Set) to preserve selection order:
 *   - list[0] = left axis (start), list[1] = right axis (end).
 *   Max 2 series per graph. FIFO: adding a 3rd deselects the oldest.
 */
data class GraphConfig(
    val bgOverlays: List<SeriesType> = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS),
    val bgHeight: Int = DEFAULT_BG_GRAPH_HEIGHT_DP,
    val secondaryGraphs: List<SecondaryGraph> = emptyList()
) {

    companion object {

        /** Maximum number of secondary graphs allowed */
        const val MAX_SECONDARY_GRAPHS = 5

        /** Default graph height in dp (minimum value — user-adjustable up to [MAX_GRAPH_HEIGHT_DP]) */
        const val DEFAULT_GRAPH_HEIGHT_DP = 100

        /**
         * Default height of the BG graph in dp. Higher than the others: its axis starts at 0 and
         * basal sits under the low mark, so the curve needs the room.
         */
        const val DEFAULT_BG_GRAPH_HEIGHT_DP = 200

        /** Maximum graph height in dp (2.5x default) */
        const val MAX_GRAPH_HEIGHT_DP = 250
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
