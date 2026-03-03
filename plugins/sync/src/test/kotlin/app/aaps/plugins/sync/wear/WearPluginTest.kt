package app.aaps.plugins.sync.wear

import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.plugins.sync.wear.wearintegration.DataHandlerMobile
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobileHelper
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class WearPluginTest : TestBaseWithProfile() {

    @Mock lateinit var dataHandlerMobile: DataHandlerMobile
    @Mock lateinit var dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper
    @Mock lateinit var versionCheckerUtils: VersionCheckerUtils

    private lateinit var wearPlugin: WearPlugin
    private lateinit var rateLimit: RateLimit

    init {
        addInjector {
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        wearPlugin = WearPlugin(aapsLogger, rh, aapsSchedulers, preferences, fabricPrivacy, rxBus, context, dataHandlerMobile, dataLayerListenerServiceMobileHelper, config, dateUtil, versionCheckerUtils)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        wearPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
