package app.aaps.plugins.sync.tidepool

import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.comm.UploadChunk
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class TidepoolPluginTest : TestBaseWithProfile() {

    @Mock lateinit var tidepoolUploader: TidepoolUploader
    @Mock lateinit var uploadChunk: UploadChunk
    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var authFlowOut: AuthFlowOut

    private lateinit var tidepoolPlugin: TidepoolPlugin
    private lateinit var rateLimit: RateLimit

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        tidepoolPlugin = TidepoolPlugin(
            aapsLogger, rh, preferences, aapsSchedulers, rxBus, context, fabricPrivacy, tidepoolUploader, uploadChunk, rateLimit, receiverDelegate, uiInteraction, authFlowOut
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        tidepoolPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
