package app.aaps.database

import app.aaps.database.entities.data.Block
import app.aaps.database.entities.data.TargetBlock
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The profile block columns have to stay readable across the switch away from `org.json`.
 *
 * `org.json` is Android only, so it could not follow the entities into commonMain. Its output is not
 * reproducible byte for byte either: it printed a whole double as `1` rather than `1.0`, and it sorted
 * the keys of a TargetBlock (`duration, highTarget, lowTarget`) instead of keeping the declared order.
 * Neither difference matters to a reader, so what has to hold - and what is asserted here - is that the
 * current code reads what old builds wrote, and that its own output round trips.
 *
 * The literals below are exactly what `org.json` produced, captured from the previous implementation.
 */
internal class ProfileBlocksFormatTest {

    private val converters = Converters()

    @Test
    fun `reads the basal blocks an older build wrote`() {
        val stored = """[{"duration":3600000,"amount":1},{"duration":7200000,"amount":0.825}]"""
        assertThat(converters.toListOfBlocks(stored))
            .isEqualTo(listOf(Block(3600000L, 1.0), Block(7200000L, 0.825)))
    }

    @Test
    fun `reads the target blocks an older build wrote, including its key order`() {
        val stored = """[{"duration":3600000,"highTarget":7,"lowTarget":5},{"duration":7200000,"highTarget":6.25,"lowTarget":4.5}]"""
        assertThat(converters.toListOfTargetBlocks(stored))
            .isEqualTo(listOf(TargetBlock(3600000L, 5.0, 7.0), TargetBlock(7200000L, 4.5, 6.25)))
    }

    @Test
    fun `blocks round trip`() {
        val blocks = listOf(Block(3600000L, 1.0), Block(7200000L, 0.825))
        assertThat(converters.toListOfBlocks(converters.fromListOfBlocks(blocks))).isEqualTo(blocks)
        val targets = listOf(TargetBlock(3600000L, 5.0, 7.0), TargetBlock(7200000L, 4.5, 6.25))
        assertThat(converters.toListOfTargetBlocks(converters.fromListOfTargetBlocks(targets))).isEqualTo(targets)
    }

    @Test
    fun `null and empty stay as they were`() {
        assertThat(converters.fromListOfBlocks(null)).isNull()
        assertThat(converters.toListOfBlocks(null)).isNull()
        assertThat(converters.fromListOfBlocks(emptyList())).isEqualTo("[]")
        assertThat(converters.toListOfBlocks("[]")).isEmpty()
    }
}
