package app.aaps.database.persistence.converters

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.database.entities.embedments.InterfaceIDs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class PumpTypeExtensionTest {

    @Test
    fun `domain round trip is stable for every PumpType value`() {
        PumpType.entries.forEach { original ->
            assertEquals(original, original.toDb().fromDb())
        }
    }

    @Test
    fun `entity round trip is stable for every value`() {
        // The two enums have the same 37 values and toDb() is injective, so the mapping is a clean
        // bijection — every entity value round-trips too.
        InterfaceIDs.PumpType.entries.forEach { original ->
            assertEquals(original, original.fromDb().toDb())
        }
    }

    @Test
    fun `every domain value maps to a distinct entity value - injective`() {
        val mapped = PumpType.entries.map { it.toDb() }
        assertEquals(mapped.size, mapped.toSet().size, "mapped values must be unique")
    }

    @Test
    fun `INSIGHT variants are a name-swapped bijection`() {
        // The domain "VIRTUAL"/plain names are swapped relative to the entity plain/"BLUETOOTH" names,
        // but the mapping is consistent in both directions (not lossy).
        assertEquals(InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT, PumpType.ACCU_CHEK_INSIGHT_VIRTUAL.toDb())
        assertEquals(InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH, PumpType.ACCU_CHEK_INSIGHT.toDb())
        assertEquals(PumpType.ACCU_CHEK_INSIGHT_VIRTUAL, InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT.fromDb())
        assertEquals(PumpType.ACCU_CHEK_INSIGHT, InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH.fromDb())
    }

    @Test
    fun `representative renamed values map across the naming differences`() {
        assertEquals(InterfaceIDs.PumpType.MEDTRONIC_512_517, PumpType.MEDTRONIC_512_712.toDb())
        assertEquals(InterfaceIDs.PumpType.EOPATCH2, PumpType.EOFLOW_EOPATCH2.toDb())
        assertEquals(InterfaceIDs.PumpType.MEDTRUM, PumpType.MEDTRUM_NANO.toDb())

        assertEquals(PumpType.MEDTRONIC_512_712, InterfaceIDs.PumpType.MEDTRONIC_512_517.fromDb())
        assertEquals(PumpType.EOFLOW_EOPATCH2, InterfaceIDs.PumpType.EOPATCH2.fromDb())
        assertEquals(PumpType.MEDTRUM_NANO, InterfaceIDs.PumpType.MEDTRUM.fromDb())
    }
}
