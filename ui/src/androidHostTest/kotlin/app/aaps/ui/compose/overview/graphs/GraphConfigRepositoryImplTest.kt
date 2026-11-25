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
 *
 * Documents without `"version":3` come from a build that had a fixed IOB graph, which the reader
 * turns into the first graph below BG. Tests about something else write version 3, so that graph is
 * not in the way.
 */
class GraphConfigRepositoryImplTest {

    private val iobGraphWithActivity = SecondaryGraph(listOf(SeriesType.IOB, SeriesType.ACTIVITY))

    @Test
    fun `a configuration survives a round trip`() {
        val config = GraphConfig(
            bgOverlays = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS),
            bgHeight = 200,
            secondaryGraphs = listOf(SecondaryGraph(listOf(SeriesType.IOB), 150), SecondaryGraph(listOf(SeriesType.COB), 180))
        )

        val restored = GraphConfigRepositoryImpl.fromJson(GraphConfigRepositoryImpl.toJson(config))

        assertThat(restored).isEqualTo(config)
    }

    @Test
    fun `a new configuration has no graph below BG`() {
        assertThat(GraphConfig().secondaryGraphs).isEmpty()
        assertThat(GraphConfigRepositoryImpl.fromJson(GraphConfigRepositoryImpl.toJson(GraphConfig())).secondaryGraphs).isEmpty()
    }

    @Test
    fun `an app used before with nothing saved keeps the graphs it showed until now`() {
        // Most users never opened the graph editor, so nothing is saved for them
        assertThat(GraphConfigRepositoryImpl.blankConfig(usedBefore = true).secondaryGraphs)
            .containsExactly(iobGraphWithActivity, SecondaryGraph(listOf(SeriesType.COB))).inOrder()
    }

    @Test
    fun `a new install with nothing saved has no graph below BG`() {
        assertThat(GraphConfigRepositoryImpl.blankConfig(usedBefore = false)).isEqualTo(GraphConfig())
    }

    @Test
    fun `the legacy shape, a bare array of series names, still reads`() {
        // Before secondary graphs had their own height, each entry was just the list of series.
        val legacy = """{"secondaryGraphs":[["COB"]]}"""

        val restored = GraphConfigRepositoryImpl.fromJson(legacy)

        assertThat(restored.secondaryGraphs).hasSize(2)
        assertThat(restored.secondaryGraphs[1].series).containsExactly(SeriesType.COB)
        assertThat(restored.secondaryGraphs[1].height).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
    }

    @Test
    fun `a series name this version does not know is skipped, not fatal`() {
        // Forward compatibility: a newer build may have written a series this one cannot name.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":3,"secondaryGraphs":[{"series":["COB","NOT_A_SERIES"]}]}""")

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB)
    }

    @Test
    fun `IOB is a normal choice for a graph below BG`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":3,"secondaryGraphs":[{"series":["IOB","COB"]}]}""")

        assertThat(restored.secondaryGraphs).containsExactly(SecondaryGraph(listOf(SeriesType.IOB, SeriesType.COB)))
    }

    @Test
    fun `the fixed IOB graph of an older config becomes the first graph below BG`() {
        val restored = GraphConfigRepositoryImpl.fromJson(
            """{"version":2,"iobOverlays":["ACTIVITY"],"iobHeight":150,"secondaryGraphs":[{"series":["COB"],"height":120}]}"""
        )

        assertThat(restored.secondaryGraphs).containsExactly(
            SecondaryGraph(listOf(SeriesType.IOB, SeriesType.ACTIVITY), 150),
            SecondaryGraph(listOf(SeriesType.COB), 120)
        ).inOrder()
    }

    @Test
    fun `the migrated IOB graph has no Activity when its overlay was off`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":2,"iobOverlays":[]}""")

        assertThat(restored.secondaryGraphs).containsExactly(SecondaryGraph(listOf(SeriesType.IOB)))
    }

    @Test
    fun `an IOB left in a graph of an older config is not shown twice`() {
        // Before the fixed IOB graph, IOB could be in a configurable graph; the migration adds its own.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":2,"secondaryGraphs":[{"series":["IOB","COB"]},{"series":["IOB"]}]}""")

        assertThat(restored.secondaryGraphs).containsExactly(iobGraphWithActivity, SecondaryGraph(listOf(SeriesType.COB))).inOrder()
    }

    @Test
    fun `a full list of an older config loses its last graph to the migrated IOB graph`() {
        val five = (1..5).joinToString(",") { """{"series":["COB"]}""" }
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":2,"secondaryGraphs":[$five]}""")

        assertThat(restored.secondaryGraphs).hasSize(GraphConfig.MAX_SECONDARY_GRAPHS)
        assertThat(restored.secondaryGraphs.first()).isEqualTo(iobGraphWithActivity)
    }

    @Test
    fun `a graph left with no series at all is dropped`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":3,"secondaryGraphs":[{"series":["NOT_A_SERIES"]}]}""")

        assertThat(restored.secondaryGraphs).isEmpty()
    }

    @Test
    fun `a repeated series is kept once, and at most two are kept`() {
        val restored = GraphConfigRepositoryImpl.fromJson(
            """{"version":3,"secondaryGraphs":[{"series":["COB","COB","ACTIVITY","DEVIATIONS"]}]}"""
        )

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB, SeriesType.ACTIVITY).inOrder()
    }

    @Test
    fun `heights are clamped to the allowed range`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"version":3,"bgHeight":10,"secondaryGraphs":[{"series":["COB"],"height":100000}]}""")

        assertThat(restored.bgHeight).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
        assertThat(restored.secondaryGraphs.first().height).isEqualTo(GraphConfig.MAX_GRAPH_HEIGHT_DP)
    }

    @Test
    fun `missing heights fall back to the defaults and BG is higher`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{}""")

        assertThat(restored.bgHeight).isEqualTo(200)
        assertThat(restored.secondaryGraphs.first().height).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
        assertThat(GraphConfig().bgHeight).isEqualTo(200)
    }

    @Test
    fun `an old config with the old default BG height gets the new default`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgHeight":100,"iobHeight":100}""")

        assertThat(restored.bgHeight).isEqualTo(200)
        assertThat(restored.secondaryGraphs.first().height).isEqualTo(100)
    }

    @Test
    fun `an old config with a BG height the user changed keeps it`() {
        assertThat(GraphConfigRepositoryImpl.fromJson("""{"bgHeight":150}""").bgHeight).isEqualTo(150)
    }

    @Test
    fun `a BG height of 100 saved after the migration stays`() {
        val saved = GraphConfigRepositoryImpl.toJson(GraphConfig(bgHeight = 100))

        assertThat(GraphConfigRepositoryImpl.fromJson(saved).bgHeight).isEqualTo(100)
    }

    @Test
    fun `missing overlays fall back to the defaults`() {
        val restored = GraphConfigRepositoryImpl.fromJson("{}")

        assertThat(restored.bgOverlays).containsExactly(SeriesType.ACTIVITY, SeriesType.PREDICTIONS).inOrder()
        // The fixed IOB graph had Activity on by default, so the migrated graph has it too
        assertThat(restored.secondaryGraphs.first()).isEqualTo(iobGraphWithActivity)
    }

    @Test
    fun `an empty overlay list is kept empty, not replaced by the default`() {
        // Written explicitly means the user turned them all off - that is not the same as absent.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgOverlays":[]}""")

        assertThat(restored.bgOverlays).isEmpty()
    }
}
