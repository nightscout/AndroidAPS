package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the stored format of the overview graph configuration.
 *
 * This is user configuration in preferences, and it still has to read documents written by older
 * versions - the secondary graph entries used to be bare arrays of series names before they gained a
 * height. These were written against the `org.json` implementation and kept while it moved to
 * kotlinx.serialization, so they say the swap changed nothing that is stored.
 */
class GraphConfigRepositoryImplTest {

    @Test
    fun `a configuration survives a round trip`() {
        val config = GraphConfig(
            bgOverlays = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS),
            iobOverlays = listOf(SeriesType.ACTIVITY),
            bgHeight = 200,
            iobHeight = 150,
            secondaryGraphs = listOf(SecondaryGraph(listOf(SeriesType.COB), 180))
        )

        val restored = GraphConfigRepositoryImpl.fromJson(GraphConfigRepositoryImpl.toJson(config))

        assertThat(restored).isEqualTo(config)
    }

    @Test
    fun `the legacy shape, a bare array of series names, still reads`() {
        // Before secondary graphs had their own height, each entry was just the list of series.
        val legacy = """{"secondaryGraphs":[["COB"]]}"""

        val restored = GraphConfigRepositoryImpl.fromJson(legacy)

        assertThat(restored.secondaryGraphs).hasSize(1)
        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB)
        assertThat(restored.secondaryGraphs.first().height).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
    }

    @Test
    fun `a series name this version does not know is skipped, not fatal`() {
        // Forward compatibility: a newer build may have written a series this one cannot name.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"secondaryGraphs":[{"series":["COB","NOT_A_SERIES"]}]}""")

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB)
    }

    @Test
    fun `IOB is dropped from a configurable graph`() {
        // IOB has its own fixed graph now; an older document may still list it here.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"secondaryGraphs":[{"series":["IOB","COB"]}]}""")

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB)
    }

    @Test
    fun `a graph left with no series at all is dropped`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"secondaryGraphs":[{"series":["IOB"]}]}""")

        assertThat(restored.secondaryGraphs).isEmpty()
    }

    @Test
    fun `a repeated series is kept once, and at most two are kept`() {
        val restored = GraphConfigRepositoryImpl.fromJson(
            """{"secondaryGraphs":[{"series":["COB","COB","ACTIVITY","DEVIATIONS"]}]}"""
        )

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB, SeriesType.ACTIVITY).inOrder()
    }

    @Test
    fun `heights are clamped to the allowed range`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgHeight":10,"iobHeight":100000}""")

        assertThat(restored.bgHeight).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
        assertThat(restored.iobHeight).isEqualTo(GraphConfig.MAX_GRAPH_HEIGHT_DP)
    }

    @Test
    fun `missing overlays fall back to the defaults`() {
        val restored = GraphConfigRepositoryImpl.fromJson("{}")

        assertThat(restored.bgOverlays).containsExactly(SeriesType.ACTIVITY, SeriesType.PREDICTIONS).inOrder()
        assertThat(restored.iobOverlays).containsExactly(SeriesType.ACTIVITY)
    }

    @Test
    fun `an empty overlay list is kept empty, not replaced by the default`() {
        // Written explicitly means the user turned them all off - that is not the same as absent.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgOverlays":[]}""")

        assertThat(restored.bgOverlays).isEmpty()
    }
}
