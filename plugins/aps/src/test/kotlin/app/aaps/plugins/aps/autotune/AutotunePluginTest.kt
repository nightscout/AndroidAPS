package app.aaps.plugins.aps.autotune

import android.content.SharedPreferences
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AutotunePluginTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var autotuneIob: AutotuneIob
    @Mock lateinit var autotunePrep: AutotunePrep
    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var autotuneCore: AutotuneCore
    @Mock lateinit var uel: UserEntryLogger
    private lateinit var autotunePlugin: AutotunePlugin

    init {
        addInjector {
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        autotunePlugin = AutotunePlugin(
            injector, rh, sp, preferences, rxBus, profileFunction, dateUtil, activePlugin,
            autotuneFS, autotuneIob, autotunePrep, autotuneCore, config, uel, aapsLogger, instantiator
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        autotunePlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
