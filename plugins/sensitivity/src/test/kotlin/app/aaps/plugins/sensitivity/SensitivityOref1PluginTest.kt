package app.aaps.plugins.sensitivity

import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class SensitivityOref1PluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin

    @BeforeEach fun prepare() {
        preferenceManager = PreferenceManager(context)
        sensitivityOref1Plugin = SensitivityOref1Plugin(
            aapsLogger, rh, preferences, profileFunction, dateUtil, persistenceLayer
        )
    }

    @Test
    fun isMinCarbsAbsorptionDynamic() {
        assertThat(sensitivityOref1Plugin.isMinCarbsAbsorptionDynamic).isFalse()
    }

    @Test
    fun isOref1() {
        assertThat(sensitivityOref1Plugin.isOref1).isTrue()
    }

    @Test
    fun getId() {
        assertThat(sensitivityOref1Plugin.id).isEqualTo(Sensitivity.SensitivityType.SENSITIVITY_OREF1)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        sensitivityOref1Plugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
