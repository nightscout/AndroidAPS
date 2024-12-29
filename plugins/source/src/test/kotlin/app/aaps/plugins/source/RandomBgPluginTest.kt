package app.aaps.plugins.source

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class RandomBgPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump

    private lateinit var randomBgPlugin: RandomBgPlugin

    init {
        addInjector {
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveIntPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.profileUtil = profileUtil
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        preferenceManager = PreferenceManager(context)
        randomBgPlugin = RandomBgPlugin(context, rh, aapsLogger, persistenceLayer, virtualPump, preferences, config)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        randomBgPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
