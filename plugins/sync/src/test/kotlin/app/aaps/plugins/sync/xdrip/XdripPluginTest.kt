package app.aaps.plugins.sync.xdrip

import android.content.SharedPreferences
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class XdripPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var loop: Loop
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var xdripPlugin: XdripPlugin
    private lateinit var rateLimit: RateLimit

    init {
        addInjector {
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
        }
    }

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        xdripPlugin = XdripPlugin(
            preferences, profileFunction, profileUtil, rh, aapsSchedulers, context, fabricPrivacy, loop, iobCobCalculator, processedTbrEbData, rxBus, uiInteraction, dateUtil, aapsLogger, config, decimalFormatter
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        xdripPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
