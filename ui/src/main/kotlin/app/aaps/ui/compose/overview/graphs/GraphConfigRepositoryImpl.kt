package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
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
            val graphsArray = JSONArray()
            for (graph in config.secondaryGraphs) {
                val seriesArray = JSONArray()
                for (series in graph) seriesArray.put(series.name)
                graphsArray.put(seriesArray)
            }
            obj.put(KEY_SECONDARY_GRAPHS, graphsArray)
            return obj.toString()
        }

        fun fromJson(json: String): GraphConfig {
            val obj = JSONObject(json)
            val bgOverlays = overlaysFromJson(obj.optJSONArray(KEY_BG_OVERLAYS), listOf(SeriesType.ACTIVITY))
            val iobOverlays = overlaysFromJson(obj.optJSONArray(KEY_IOB_OVERLAYS), listOf(SeriesType.ACTIVITY))
            val graphs = mutableListOf<List<SeriesType>>()
            val graphsArray = obj.optJSONArray(KEY_SECONDARY_GRAPHS)
            if (graphsArray != null) {
                for (i in 0 until graphsArray.length()) {
                    val seriesArray = graphsArray.getJSONArray(i)
                    val series = mutableListOf<SeriesType>()
                    for (j in 0 until seriesArray.length()) {
                        try {
                            val type = SeriesType.valueOf(seriesArray.getString(j))
                            // IOB is now a fixed graph — strip it from configurable graphs (legacy migration)
                            if (type == SeriesType.IOB) continue
                            if (type !in series) series.add(type) // preserve order, no duplicates
                        } catch (_: IllegalArgumentException) {
                            // Skip unknown series (forward compat)
                        }
                    }
                    if (series.isNotEmpty()) graphs.add(series.take(2)) // max 2 per graph
                }
            }
            return GraphConfig(bgOverlays = bgOverlays, iobOverlays = iobOverlays, secondaryGraphs = graphs)
        }
    }
}
