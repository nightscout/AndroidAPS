package app.aaps.database.persistence.converters

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class PumpTypeExtensionTest {

    @Test
    fun `domain round trip is stable for every PumpType value`() {
        PumpType.entries.forEach { original ->
            assertThat(original.toDb().fromDb()).isEqualTo(original)
        }
    }

    @Test
    fun `entity round trip is stable for every value`() {
        // The two enums have the same 37 values and toDb() is injective, so the mapping is a clean
        // bijection — every entity value round-trips too.
        InterfaceIDs.PumpType.entries.forEach { original ->
            assertThat(original.fromDb().toDb()).isEqualTo(original)
        }
    }

    @Test
    fun `every domain value maps to a distinct entity value (injective)`() {
        val mapped = PumpType.entries.map { it.toDb() }
        assertThat(mapped).containsNoDuplicates()
    }

    @Test
    fun `INSIGHT variants are a name-swapped bijection`() {
        // The domain "VIRTUAL"/plain names are swapped relative to the entity plain/"BLUETOOTH" names,
        // but the mapping is consistent in both directions (not lossy).
        assertThat(PumpType.ACCU_CHEK_INSIGHT_VIRTUAL.toDb()).isEqualTo(InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT)
        assertThat(PumpType.ACCU_CHEK_INSIGHT.toDb()).isEqualTo(InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH)
        assertThat(InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT.fromDb()).isEqualTo(PumpType.ACCU_CHEK_INSIGHT_VIRTUAL)
        assertThat(InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH.fromDb()).isEqualTo(PumpType.ACCU_CHEK_INSIGHT)
    }

    @Test
    fun `representative renamed values map across the naming differences`() {
        assertThat(PumpType.MEDTRONIC_512_712.toDb()).isEqualTo(InterfaceIDs.PumpType.MEDTRONIC_512_517)
        assertThat(PumpType.EOFLOW_EOPATCH2.toDb()).isEqualTo(InterfaceIDs.PumpType.EOPATCH2)
        assertThat(PumpType.MEDTRUM_NANO.toDb()).isEqualTo(InterfaceIDs.PumpType.MEDTRUM)

        assertThat(InterfaceIDs.PumpType.MEDTRONIC_512_517.fromDb()).isEqualTo(PumpType.MEDTRONIC_512_712)
        assertThat(InterfaceIDs.PumpType.EOPATCH2.fromDb()).isEqualTo(PumpType.EOFLOW_EOPATCH2)
        assertThat(InterfaceIDs.PumpType.MEDTRUM.fromDb()).isEqualTo(PumpType.MEDTRUM_NANO)
    }
}
