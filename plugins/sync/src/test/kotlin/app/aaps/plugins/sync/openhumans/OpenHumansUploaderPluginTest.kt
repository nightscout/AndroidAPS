package app.aaps.plugins.sync.openhumans

import android.content.SharedPreferences
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.openhumans.delegates.OHAppIDDelegate
import app.aaps.plugins.sync.openhumans.delegates.OHCounterDelegate
import app.aaps.plugins.sync.openhumans.delegates.OHStateDelegate
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class OpenHumansUploaderPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var openHumansAPI: OpenHumansAPI

    private lateinit var openHumansUploaderPlugin: OpenHumansUploaderPlugin
    private lateinit var stateDelegate: OHStateDelegate
    private lateinit var counterDelegate: OHCounterDelegate
    private lateinit var appIdDelegate: OHAppIDDelegate

    init {
        addInjector {
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        stateDelegate = OHStateDelegate(sp)
        counterDelegate = OHCounterDelegate(sp)
        appIdDelegate = OHAppIDDelegate(sp)
        openHumansUploaderPlugin = OpenHumansUploaderPlugin(rh, aapsLogger, preferences, context, persistenceLayer, openHumansAPI, stateDelegate, counterDelegate, appIdDelegate, rxBus)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        openHumansUploaderPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
