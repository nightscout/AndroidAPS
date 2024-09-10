package app.aaps.plugins.sensitivity

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class SensitivityAAPSPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var sharedPrefs: SharedPreferences

    private lateinit var sensitivityAAPSPlugin: SensitivityAAPSPlugin

    init {
        addInjector {
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveDoublePreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
        }
    }

    @BeforeEach fun prepare() {
        preferenceManager = PreferenceManager(context)
        sensitivityAAPSPlugin = SensitivityAAPSPlugin(
            aapsLogger, rh, sp, preferences, profileFunction, dateUtil, persistenceLayer
        )
    }

    @Test
    fun isMinCarbsAbsorptionDynamic() {
        assertThat(sensitivityAAPSPlugin.isMinCarbsAbsorptionDynamic).isTrue()
    }

    @Test
    fun isOref1() {
        assertThat(sensitivityAAPSPlugin.isOref1).isFalse()
    }

    @Test
    fun getId() {
        assertThat(sensitivityAAPSPlugin.id).isEqualTo(Sensitivity.SensitivityType.SENSITIVITY_AAPS)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        sensitivityAAPSPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
