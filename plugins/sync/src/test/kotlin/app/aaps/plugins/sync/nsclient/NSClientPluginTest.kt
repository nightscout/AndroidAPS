package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class NSClientPluginTest : TestBaseWithProfile() {

    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV1: DataSyncSelectorV1
    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var nsClientRepository: NSClientRepository
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var nsClientPlugin: NSClientPlugin

    @BeforeEach fun prepare() {
        nsClientPlugin = NSClientPlugin(
            aapsLogger, rxBus, rh, context, preferences, receiverDelegate, dataSyncSelectorV1,
            dateUtil, profileUtil, nsSettingsStatus, decimalFormatter, nsClientRepository, persistenceLayer, uel
        )
    }

    @Test
    fun specialEnableConditionTest() {
        assertThat(nsClientPlugin.specialEnableCondition()).isTrue()
    }

    @Test
    fun specialShowInListConditionTest() {
        assertThat(nsClientPlugin.specialShowInListCondition()).isTrue()
    }

}
