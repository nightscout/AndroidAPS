package app.aaps.plugins.sync.xdrip

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
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

    @Mock lateinit var loop: Loop
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider

    private lateinit var xdripPlugin: XdripPlugin
    private lateinit var rateLimit: RateLimit

    init {
        addInjector {
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.config = config
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
            }
        }
    }

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        xdripPlugin = XdripPlugin(
            aapsLogger, rh, preferences, profileFunction, profileUtil, aapsSchedulers, context, fabricPrivacy, loop, iobCobCalculator, processedTbrEbData, rxBus, uiInteraction, dateUtil, config, decimalFormatter, glucoseStatusProvider
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        xdripPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
