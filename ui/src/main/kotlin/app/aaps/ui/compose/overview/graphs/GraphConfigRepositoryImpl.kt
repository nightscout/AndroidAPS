package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphConfigRepositoryImpl @Inject constructor(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) : GraphConfigRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _graphConfigFlow = MutableStateFlow(load())
    override val graphConfigFlow: StateFlow<GraphConfig> = _graphConfigFlow.asStateFlow()

    init {
        // Sync from preferences when changed externally (e.g. import/restore)
        scope.launch {
            preferences.observe(StringNonKey.ComposeGraphConfig)
                .drop(1) // Skip initial (already loaded)
                .collect { json -> _graphConfigFlow.value = parseJson(json) }
        }
    }

    override fun update(config: GraphConfig) {
        _graphConfigFlow.value = config
        preferences.put(StringNonKey.ComposeGraphConfig, toJson(config))
    }

    private fun load(): GraphConfig = parseJson(preferences.get(StringNonKey.ComposeGraphConfig))

    private fun parseJson(json: String): GraphConfig {
        if (json.isBlank()) return GraphConfig()
        return try {
            fromJson(json)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to parse graph config, using defaults", e)
            GraphConfig()
        }
    }

    companion object {

        private const val KEY_SECONDARY_GRAPHS = "secondaryGraphs"
        private const val KEY_BG_OVERLAYS = "bgOverlays"
        private const val KEY_IOB_OVERLAYS = "iobOverlays"
        private const val KEY_BG_HEIGHT = "bgHeight"
        private const val KEY_IOB_HEIGHT = "iobHeight"
        private const val KEY_SERIES = "series"
        private const val KEY_HEIGHT = "height"

        private fun overlaysToJson(overlays: List<SeriesType>): JSONArray {
            val arr = JSONArray()
            for (type in overlays) arr.put(type.name)
            return arr
        }

        private fun overlaysFromJson(arr: JSONArray?, default: List<SeriesType>): List<SeriesType> {
            if (arr == null) return default
            val out = mutableListOf<SeriesType>()
            for (i in 0 until arr.length()) {
                try {
                    val type = SeriesType.valueOf(arr.getString(i))
                    if (type !in out) out.add(type)
                } catch (_: IllegalArgumentException) {
                    // Skip unknown types (forward compat)
                }
            }
            return out
        }

        fun toJson(config: GraphConfig): String {
            val obj = JSONObject()
            obj.put(KEY_BG_OVERLAYS, overlaysToJson(config.bgOverlays))
            obj.put(KEY_IOB_OVERLAYS, overlaysToJson(config.iobOverlays))
            obj.put(KEY_BG_HEIGHT, config.bgHeight)
            obj.put(KEY_IOB_HEIGHT, config.iobHeight)
            val graphsArray = JSONArray()
            for (graph in config.secondaryGraphs) {
                val seriesArray = JSONArray()
                for (series in graph.series) seriesArray.put(series.name)
                val entry = JSONObject()
                entry.put(KEY_SERIES, seriesArray)
                entry.put(KEY_HEIGHT, graph.height)
                graphsArray.put(entry)
            }
            obj.put(KEY_SECONDARY_GRAPHS, graphsArray)
            return obj.toString()
        }

        private fun parseSeriesArray(seriesArray: JSONArray): List<SeriesType> {
            val series = mutableListOf<SeriesType>()
            for (j in 0 until seriesArray.length()) {
                try {
                    val type = SeriesType.valueOf(seriesArray.getString(j))
                    // IOB is now a fixed graph — strip it from configurable graphs (legacy migration)
                    if (type == SeriesType.IOB) continue
                    if (type !in series) series.add(type)
                } catch (_: IllegalArgumentException) {
                    // Skip unknown series (forward compat)
                }
            }
            return series.take(2)
        }

        fun fromJson(json: String): GraphConfig {
            val obj = JSONObject(json)
            val bgOverlays = overlaysFromJson(obj.optJSONArray(KEY_BG_OVERLAYS), listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS))
            val iobOverlays = overlaysFromJson(obj.optJSONArray(KEY_IOB_OVERLAYS), listOf(SeriesType.ACTIVITY))
            val bgHeight = obj.optInt(KEY_BG_HEIGHT, GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
                .coerceIn(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP, GraphConfig.MAX_GRAPH_HEIGHT_DP)
            val iobHeight = obj.optInt(KEY_IOB_HEIGHT, GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
                .coerceIn(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP, GraphConfig.MAX_GRAPH_HEIGHT_DP)
            val graphs = mutableListOf<SecondaryGraph>()
            val graphsArray = obj.optJSONArray(KEY_SECONDARY_GRAPHS)
            if (graphsArray != null) {
                for (i in 0 until graphsArray.length()) {
                    // Legacy format: element is a JSONArray of series names.
                    // New format: element is a JSONObject { series: [...], height: Int }.
                    val raw = graphsArray.opt(i)
                    val (series, height) = when (raw) {
                        is JSONArray  -> parseSeriesArray(raw) to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                        is JSONObject -> {
                            val s = raw.optJSONArray(KEY_SERIES)?.let { parseSeriesArray(it) } ?: emptyList()
                            val h = raw.optInt(KEY_HEIGHT, GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
                                .coerceIn(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP, GraphConfig.MAX_GRAPH_HEIGHT_DP)
                            s to h
                        }
                        else          -> emptyList<SeriesType>() to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                    }
                    if (series.isNotEmpty()) graphs.add(SecondaryGraph(series, height))
                }
            }
            return GraphConfig(
                bgOverlays = bgOverlays,
                iobOverlays = iobOverlays,
                bgHeight = bgHeight,
                iobHeight = iobHeight,
                secondaryGraphs = graphs
            )
        }
    }
}
