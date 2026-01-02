package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class NSClientPluginTest : TestBaseWithProfile() {

    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV1: DataSyncSelectorV1
    @Mock lateinit var nsSettingsStatus: NSSettingsStatus

    private lateinit var nsClientPlugin: NSClientPlugin

    @BeforeEach fun prepare() {
        nsClientPlugin = NSClientPlugin(
            aapsLogger, aapsSchedulers, rxBus, rh, context, fabricPrivacy, preferences, receiverDelegate, dataSyncSelectorV1,
            dateUtil, profileUtil, nsSettingsStatus, decimalFormatter
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

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        nsClientPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
