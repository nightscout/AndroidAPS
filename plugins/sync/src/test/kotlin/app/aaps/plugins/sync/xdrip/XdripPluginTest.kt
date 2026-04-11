package app.aaps.plugins.sync.xdrip

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock

class XdripPluginTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var xdripMvvmRepository: XdripMvvmRepository
    @Mock lateinit var dataSyncSelector: DataSyncSelectorXdrip
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var xdripPlugin: XdripPlugin
    private lateinit var rateLimit: RateLimit

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
}
