package app.aaps.plugins.automation

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.ui.TimerUtil
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AutomationPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var loop: Loop
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var timerUtil: TimerUtil
    private lateinit var automationPlugin: AutomationPlugin

    init {
        addInjector {
            if (it is AdaptiveListPreference) {
                it.preferences = preferences
            }
        }
    }

    @BeforeEach fun prepare() {
        automationPlugin = AutomationPlugin(
            injector, aapsLogger, rh, preferences, context, fabricPrivacy, loop, rxBus, constraintChecker,
            aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin, timerUtil
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        automationPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
