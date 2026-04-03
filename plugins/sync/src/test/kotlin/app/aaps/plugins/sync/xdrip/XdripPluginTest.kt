package app.aaps.plugins.sync.xdrip

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class XdripPluginTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var xdripMvvmRepository: XdripMvvmRepository
    @Mock lateinit var dataSyncSelector: DataSyncSelectorXdrip
    @Mock lateinit var persistenceLayer: PersistenceLayer

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
            aapsLogger,
            rh,
            preferences,
            profileFunction,
            profileUtil,
            aapsSchedulers,
            context,
            fabricPrivacy,
            loop,
            iobCobCalculator,
            processedTbrEbData,
            rxBus,
            dateUtil,
            config,
            decimalFormatter,
            glucoseStatusProvider,
            xdripMvvmRepository,
            dataSyncSelector,
            persistenceLayer
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        xdripPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
