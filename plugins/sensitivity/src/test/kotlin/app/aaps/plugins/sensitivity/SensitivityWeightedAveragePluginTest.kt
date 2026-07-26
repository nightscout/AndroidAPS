package app.aaps.plugins.sensitivity

import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class SensitivityWeightedAveragePluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var sensitivityWeightedAveragePlugin: SensitivityWeightedAveragePlugin

    @BeforeEach fun prepare() {
        sensitivityWeightedAveragePlugin = SensitivityWeightedAveragePlugin(
            aapsLogger, rh, preferences, profileFunction, dateUtil, persistenceLayer, activePlugin
        )
    }

    @Test
    fun isMinCarbsAbsorptionDynamic() {
        assertThat(sensitivityWeightedAveragePlugin.isMinCarbsAbsorptionDynamic).isTrue()
    }

    @Test
    fun isOref1() {
        assertThat(sensitivityWeightedAveragePlugin.isOref1).isFalse()
    }

    @Test
    fun getId() {
        assertThat(sensitivityWeightedAveragePlugin.id).isEqualTo(Sensitivity.SensitivityType.SENSITIVITY_WEIGHTED)
    }
}
