package app.aaps.core.data.pump.defs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM. `kotlin.test` rather than
 * Truth and JUnit 5, because neither of those exists off the JVM.
 */
class PumpTypeTest {

    /**
     * A master sends its pump type as the description, and a client reads it back with getByDescription.
     * Two types share "Accu-Chek Insight"; the old database type came first, so a client of a real Insight
     * floored boluses by its 0.05 / 0.1 / 0.2 / 0.5 table while the pump steps by 0.01.
     */
    @Test
    fun `an Insight description gives the type of the real driver`() {
        assertEquals(PumpType.ACCU_CHEK_INSIGHT, PumpType.getByDescription(PumpType.ACCU_CHEK_INSIGHT.description))
    }

    @Test
    fun `a description gives its own type`() {
        assertEquals(PumpType.DANA_RS, PumpType.getByDescription("DanaRS"))
        assertEquals(PumpType.ACCU_CHEK_COMBO, PumpType.getByDescription("Accu-Chek Combo"))
    }

    @Test
    fun `an unknown description gives the generic type`() {
        assertEquals(PumpType.GENERIC_AAPS, PumpType.getByDescription("no such pump"))
    }

    /** USER and CACHE only tag records. As a pump they have no real limits, so they must not come back. */
    @Test
    fun `a description of a record tag gives the generic type`() {
        assertEquals(PumpType.GENERIC_AAPS, PumpType.getByDescription("USER"))
        assertEquals(PumpType.GENERIC_AAPS, PumpType.getByDescription("CACHE"))
    }
}
