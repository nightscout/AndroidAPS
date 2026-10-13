package app.aaps.pump.equil.driver.definition

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActivationProgressTest : TestBase() {

    @Test
    fun `all enum values should be accessible`() {
        val allStates = ActivationProgress.entries
        assertEquals(5, allStates.size)
        assert(allStates.contains(ActivationProgress.NONE))
        assert(allStates.contains(ActivationProgress.PRIMING))
        assert(allStates.contains(ActivationProgress.CANNULA_CHANGE))
        assert(allStates.contains(ActivationProgress.CANNULA_INSERTED))
        assert(allStates.contains(ActivationProgress.COMPLETED))
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(ActivationProgress.NONE, ActivationProgress.valueOf("NONE"))
        assertEquals(ActivationProgress.PRIMING, ActivationProgress.valueOf("PRIMING"))
        assertEquals(ActivationProgress.CANNULA_CHANGE, ActivationProgress.valueOf("CANNULA_CHANGE"))
        assertEquals(ActivationProgress.CANNULA_INSERTED, ActivationProgress.valueOf("CANNULA_INSERTED"))
        assertEquals(ActivationProgress.COMPLETED, ActivationProgress.valueOf("COMPLETED"))
    }

    @Test
    fun `enum toString should return name`() {
        assertEquals("NONE", ActivationProgress.NONE.toString())
        assertEquals("PRIMING", ActivationProgress.PRIMING.toString())
        assertEquals("CANNULA_CHANGE", ActivationProgress.CANNULA_CHANGE.toString())
        assertEquals("CANNULA_INSERTED", ActivationProgress.CANNULA_INSERTED.toString())
        assertEquals("COMPLETED", ActivationProgress.COMPLETED.toString())
    }

    @Test
    fun `enum ordinal values should be stable`() {
        assertEquals(0, ActivationProgress.NONE.ordinal)
        assertEquals(1, ActivationProgress.PRIMING.ordinal)
        assertEquals(2, ActivationProgress.CANNULA_CHANGE.ordinal)
        assertEquals(3, ActivationProgress.CANNULA_INSERTED.ordinal)
        assertEquals(4, ActivationProgress.COMPLETED.ordinal)
    }

    @Test
    fun `enum should represent activation sequence`() {
        val sequence = ActivationProgress.entries

        // Verify the sequence is in logical order
        assertEquals(ActivationProgress.NONE, sequence[0])
        assertEquals(ActivationProgress.PRIMING, sequence[1])
        assertEquals(ActivationProgress.CANNULA_CHANGE, sequence[2])
        assertEquals(ActivationProgress.CANNULA_INSERTED, sequence[3])
        assertEquals(ActivationProgress.COMPLETED, sequence[4])
    }

    @Test
    fun `ordinals should increment sequentially`() {
        for (i in 0 until ActivationProgress.entries.size - 1) {
            val current = ActivationProgress.entries[i]
            val next = ActivationProgress.entries[i + 1]
            assertEquals(current.ordinal + 1, next.ordinal)
        }
    }

    @Test
    fun `NONE should be initial state`() {
        assertEquals(0, ActivationProgress.NONE.ordinal)
    }

    @Test
    fun `COMPLETED should be final state`() {
        val lastOrdinal = ActivationProgress.entries.size - 1
        assertEquals(lastOrdinal, ActivationProgress.COMPLETED.ordinal)
    }
}
