package app.aaps.plugins.aps.autotune

import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import javax.inject.Provider

class AutotunePluginTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var autotuneIob: AutotuneIob
    @Mock lateinit var autotunePrep: AutotunePrep
    @Mock lateinit var autotuneCore: AutotuneCore
    @Mock lateinit var uel: UserEntryLogger
    private lateinit var autotunePlugin: AutotunePlugin

    init {
        addInjector {
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.config = config
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        val atProfileProvider = Provider {
            ATProfile(activePlugin, preferences, profileUtil, dateUtil, rh, profileStoreProvider, aapsLogger)
        }
        autotunePlugin = AutotunePlugin(
            aapsLogger, rh, preferences, rxBus, profileFunction, dateUtil, activePlugin,
            autotuneFS, autotuneIob, autotunePrep, autotuneCore, config, uel, profileStoreProvider, atProfileProvider
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        autotunePlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
