package app.aaps.database.persistence.converters

import app.aaps.core.data.model.ICfg
import app.aaps.database.entities.embedments.InsulinConfiguration
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [ICfg.isInhaled] has no column - see the KDoc on [fromDb]. It is reconstructed from the stored
 * peak on read, which is unambiguous because the inhaled and injected peak ranges are disjoint.
 */
class InsulinConfigurationExtensionTest {

    private fun stored(peakMinutes: Int, diaHours: Double) =
        InsulinConfiguration(
            insulinLabel = "x",
            insulinEndTime = (diaHours * 3600 * 1000).toLong(),
            insulinPeakTime = peakMinutes * 60_000L,
            concentration = 1.0
        )

    @Test fun `an inhaled peak comes back inhaled, at any peak in its range`() {
        for (m in intArrayOf(10, 15, 30))
            assertThat(stored(peakMinutes = m, diaHours = 3.0).fromDb().isInhaled).isTrue()
    }

    @Test fun `an injected peak comes back non-inhaled`() {
        for (m in intArrayOf(35, 55, 75))
            assertThat(stored(peakMinutes = m, diaHours = 8.0).fromDb().isInhaled).isFalse()
    }

    @Test fun `toDb drops the flag and keeps every stored field`() {
        val iCfg = ICfg(insulinLabel = "Afrezza", peak = 30, dia = 3.0, concentration = 1.0, isInhaled = true)

        val db = iCfg.toDb()

        assertThat(db.insulinLabel).isEqualTo("Afrezza")
        assertThat(db.insulinPeakTime).isEqualTo(30 * 60_000L)
        assertThat(db.insulinEndTime).isEqualTo((3.0 * 3600 * 1000).toLong())
        assertThat(db.concentration).isEqualTo(1.0)
        // Round-trip restores the identity even though nothing was written for it.
        assertThat(db.fromDb().isInhaled).isTrue()
    }
}
