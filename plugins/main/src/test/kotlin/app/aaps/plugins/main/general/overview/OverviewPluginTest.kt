package app.aaps.plugins.main.general.overview

import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock

class OverviewPluginTest : TestBaseWithProfile() {

    @Mock lateinit var overviewData: OverviewData
    @Mock lateinit var overviewMenus: OverviewMenus
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var overviewPlugin: OverviewPlugin

    @BeforeEach fun prepare() {
        overviewPlugin = OverviewPlugin(
            aapsLogger, rh, preferences, fabricPrivacy, rxBus,
            aapsSchedulers, overviewData, overviewMenus, context, constraintsChecker, uiInteraction, nsSettingsStatus, config, activePlugin,
            uel, notificationManager
        )
    }
}
