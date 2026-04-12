package app.aaps.plugins.sync.openhumans

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.plugins.sync.openhumans.delegates.OHAppIDDelegate
import app.aaps.plugins.sync.openhumans.delegates.OHCounterDelegate
import app.aaps.plugins.sync.openhumans.delegates.OHStateDelegate
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock

class OpenHumansUploaderPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var openHumansAPI: OpenHumansAPI

    private lateinit var openHumansUploaderPlugin: OpenHumansUploaderPlugin
    private lateinit var stateDelegate: OHStateDelegate
    private lateinit var counterDelegate: OHCounterDelegate
    private lateinit var appIdDelegate: OHAppIDDelegate

    @BeforeEach fun prepare() {
        stateDelegate = OHStateDelegate(preferences)
        counterDelegate = OHCounterDelegate(preferences)
        appIdDelegate = OHAppIDDelegate(preferences)
        openHumansUploaderPlugin = OpenHumansUploaderPlugin(rh, aapsLogger, preferences, context, persistenceLayer, openHumansAPI, stateDelegate, counterDelegate, appIdDelegate)
    }
}
