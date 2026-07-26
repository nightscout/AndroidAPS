package app.aaps.pump.insight.descriptors

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers [ParameterBlocks.Companion]: fromId / fromType round-trip every entry, plus the unknown-id miss. */
class ParameterBlocksTest {

    @Test
    fun fromIdAndFromType_roundTripEveryEntry() {
        ParameterBlocks.entries.forEach { e ->
            assertThat(ParameterBlocks.fromId(e.id)).isEqualTo(e)
            assertThat(ParameterBlocks.fromType(e.type)).isEqualTo(e)
        }
    }

    @Test
    fun fromId_nullForUnknown() {
        assertThat(ParameterBlocks.fromId(-99999)).isNull()
    }
}
