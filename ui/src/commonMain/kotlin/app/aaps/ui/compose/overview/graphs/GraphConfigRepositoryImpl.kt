package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class GraphConfigRepositoryImpl(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) : GraphConfigRepository {

    // Not the main dispatcher: all this scope does is parse a preference string into a StateFlow,
    // which is safe from any thread, and reaching for `Dispatchers.Main` in a constructor makes the
    // object graph unbuildable in a plain JVM unit test (no main looper).
    private val scope = CoroutineScope(SupervisorJob() + aapsIoDispatcher)
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

    /**
     * The saved config. When nothing is saved yet, the choice of [blankConfig] is saved at once, so it
     * is made only one time: a new install keeps its empty layout also after the setup wizard is done.
     */
    private fun load(): GraphConfig {
        val json = preferences.get(StringNonKey.ComposeGraphConfig)
        if (json.isNotBlank()) return parseJson(json)
        return blankConfig(usedBefore()).also { preferences.put(StringNonKey.ComposeGraphConfig, toJson(it)) }
    }

    /** An app that has been set up before. A new install has not finished the setup wizard yet. */
    private fun usedBefore(): Boolean = preferences.get(BooleanNonKey.GeneralSetupWizardProcessed)

    private fun parseJson(json: String): GraphConfig {
        if (json.isBlank()) return blankConfig(usedBefore())
        return try {
            fromJson(json)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to parse graph config, using defaults", e)
            // The same choice as for no config: an app set up before keeps IOB and COB below BG
            blankConfig(usedBefore())
        }
    }

    companion object {

        private const val KEY_SECONDARY_GRAPHS = "secondaryGraphs"
        private const val KEY_BG_OVERLAYS = "bgOverlays"
        private const val KEY_BG_HEIGHT = "bgHeight"

        /** Only read, from configs older than version 3: the fixed IOB graph of those versions. */
        private const val KEY_IOB_OVERLAYS = "iobOverlays"
        private const val KEY_IOB_HEIGHT = "iobHeight"
        private const val KEY_SERIES = "series"
        private const val KEY_HEIGHT = "height"
        private const val KEY_VERSION = "version"

        /**
         * Format version written by [toJson]. A config without it is version 1.
         *
         * 2: the default BG height went from 100 to 200 dp. A version 1 config with the BG graph still
         * at the old default of 100 gets the new default, see [fromJson]. A 100 the user sets after
         * that is saved as version 2 and stays.
         *
         * 3: there is no fixed IOB graph any more, IOB is a normal choice for a graph below BG. The
         * fixed IOB graph of an older config becomes the first graph below BG, with its height, and
         * with Activity as its second series when its Activity overlay was on. So nobody loses the
         * IOB graph on the update. Only a config that already had the most graphs allowed loses its
         * last one, to make room: [GraphConfig.MAX_SECONDARY_GRAPHS] did not change.
         */
        private const val CURRENT_VERSION = 3

        /**
         * The graphs below BG that the app showed by default before version 3: the fixed IOB graph with
         * its Activity overlay, and COB.
         */
        private val GRAPHS_BEFORE_VERSION_3 = listOf(
            SecondaryGraph(listOf(SeriesType.IOB, SeriesType.ACTIVITY)),
            SecondaryGraph(listOf(SeriesType.COB))
        )

        /**
         * The config when nothing is saved. Most users never open the graph editor, so nothing is
         * saved for them, and for an app that was used before this must still be the graphs it showed
         * until now ([GRAPHS_BEFORE_VERSION_3]) - they would lose IOB and COB otherwise. Only a new
         * install starts with no graph below BG, because the BG graph shows basal and treatments itself.
         */
        internal fun blankConfig(usedBefore: Boolean): GraphConfig =
            if (usedBefore) GraphConfig(secondaryGraphs = GRAPHS_BEFORE_VERSION_3) else GraphConfig()

        private fun overlaysToJson(overlays: List<SeriesType>) = buildJsonArray {
            for (type in overlays) add(type.name)
        }

        /**
         * Absent means "never written", so the caller's default applies. An empty array means the
         * user turned them all off, which is a different thing and is kept.
         */
        private fun overlaysFromJson(arr: JsonArray?, default: List<SeriesType>): List<SeriesType> {
            if (arr == null) return default
            val out = mutableListOf<SeriesType>()
            for (element in arr) {
                val type = element.seriesTypeOrNull() ?: continue // Skip unknown types (forward compat)
                if (type !in out) out.add(type)
            }
            return out
        }

        fun toJson(config: GraphConfig): String =
            buildJsonObject {
                put(KEY_VERSION, CURRENT_VERSION)
                put(KEY_BG_OVERLAYS, overlaysToJson(config.bgOverlays))
                put(KEY_BG_HEIGHT, config.bgHeight)
                put(
                    KEY_SECONDARY_GRAPHS,
                    buildJsonArray {
                        for (graph in config.secondaryGraphs) {
                            addJsonObject {
                                put(KEY_SERIES, buildJsonArray { for (series in graph.series) add(series.name) })
                                put(KEY_HEIGHT, graph.height)
                            }
                        }
                    }
                )
            }.toString()

        /**
         * The series of one graph. [stripIob] for a config older than version 3: IOB had its own
         * fixed graph then, which the migration adds, so an IOB left in a graph of such a config
         * (from before the fixed graph) would show it twice.
         */
        private fun parseSeriesArray(seriesArray: JsonArray, stripIob: Boolean): List<SeriesType> {
            val series = mutableListOf<SeriesType>()
            for (element in seriesArray) {
                val type = element.seriesTypeOrNull() ?: continue // Skip unknown series (forward compat)
                if (stripIob && type == SeriesType.IOB) continue
                if (type !in series) series.add(type)
            }
            return series.take(2)
        }

        fun fromJson(json: String): GraphConfig {
            val obj = Json.parseToJsonElement(json) as JsonObject
            val bgOverlays = overlaysFromJson(obj.array(KEY_BG_OVERLAYS), listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS))
            val version = (obj[KEY_VERSION] as? JsonPrimitive)?.let { runCatching { it.int }.getOrNull() } ?: 1
            val storedBgHeight = obj.height(KEY_BG_HEIGHT, GraphConfig.DEFAULT_BG_GRAPH_HEIGHT_DP)
            // Version 2: the old default BG height moves to the new one, see CURRENT_VERSION
            val bgHeight =
                if (version < 2 && storedBgHeight == GraphConfig.DEFAULT_GRAPH_HEIGHT_DP) GraphConfig.DEFAULT_BG_GRAPH_HEIGHT_DP
                else storedBgHeight
            val before3 = version < 3
            val graphs = mutableListOf<SecondaryGraph>()
            // Version 3: the fixed IOB graph becomes the first graph below BG, see CURRENT_VERSION
            if (before3) {
                val iobOverlays = overlaysFromJson(obj.array(KEY_IOB_OVERLAYS), listOf(SeriesType.ACTIVITY))
                val series = if (SeriesType.ACTIVITY in iobOverlays) listOf(SeriesType.IOB, SeriesType.ACTIVITY) else listOf(SeriesType.IOB)
                graphs.add(SecondaryGraph(series, obj.height(KEY_IOB_HEIGHT)))
            }
            for (raw in obj.array(KEY_SECONDARY_GRAPHS).orEmpty()) {
                // Legacy format: element is an array of series names.
                // New format: element is an object { series: [...], height: Int }.
                val (series, height) = when (raw) {
                    is JsonArray  -> parseSeriesArray(raw, before3) to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                    is JsonObject -> (raw.array(KEY_SERIES)?.let { parseSeriesArray(it, before3) } ?: emptyList()) to raw.height(KEY_HEIGHT)
                    else          -> emptyList<SeriesType>() to GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
                }
                if (series.isNotEmpty()) graphs.add(SecondaryGraph(series, height))
            }
            return GraphConfig(
                bgOverlays = bgOverlays,
                bgHeight = bgHeight,
                // The migrated IOB graph comes first, so a full list loses its last graph
                secondaryGraphs = graphs.take(GraphConfig.MAX_SECONDARY_GRAPHS)
            )
        }

        /** The named array, or null when it is absent or is something other than an array. */
        private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

        /** A stored height, defaulted when absent and always brought inside the allowed range. */
        private fun JsonObject.height(key: String, default: Int = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP): Int =
            ((this[key] as? JsonPrimitive)?.let { runCatching { it.int }.getOrNull() } ?: default)
                .coerceIn(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP, GraphConfig.MAX_GRAPH_HEIGHT_DP)

        /** The series this element names, or null if it is not a name this version knows. */
        private fun JsonElement.seriesTypeOrNull(): SeriesType? =
            (this as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.let { name -> SeriesType.entries.firstOrNull { it.name == name } }
    }
}
