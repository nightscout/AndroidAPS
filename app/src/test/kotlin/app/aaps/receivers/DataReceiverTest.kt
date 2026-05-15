package app.aaps.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.plugins.source.DexcomInbox
import app.aaps.plugins.source.XdripInbox
import app.aaps.plugins.sync.smsCommunicator.SmsInbox
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DataReceiverTest : TestBase() {

    // The System Under Test
    private lateinit var dataReceiver: DataReceiver

    // Mocks for dependencies
    @Mock private lateinit var dataInbox: DataInbox
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var context: Context
    @Mock private lateinit var bundle: Bundle

    @BeforeEach
    fun setUp() {
        // Manually inject mocks into the receiver instance
        dataReceiver = DataReceiver().also {
            it.aapsLogger = aapsLogger
            it.dataInbox = dataInbox
            it.fabricPrivacy = fabricPrivacy
        }
    }

    private fun createIntent(action: String): Intent {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(action)
        whenever(intent.extras).thenReturn(bundle)
        return intent
    }

    // ---- Inbox routing ----
    // The inline-Data paths (Poctech/Glimp/Tomato/MM640G/Syai/Si/Sino/Instara) call
    // WorkManager.getInstance(context) directly. Those branches are routing-only
    // (intent action → worker class), unit-tested only via the inbox slots below.

    @Test
    fun `xdrip BG estimate routes to XdripInbox`() {
        dataReceiver.processIntent(context, createIntent(Intents.ACTION_NEW_BG_ESTIMATE))
        verify(dataInbox).putAndEnqueue(eq(XdripInbox), eq(bundle))
    }

    @Test
    fun `SMS_RECEIVED routes to SmsInbox`() {
        dataReceiver.processIntent(context, createIntent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
        verify(dataInbox).putAndEnqueue(eq(SmsInbox), eq(bundle))
    }

    @Test
    fun `DEXCOM_BG routes to DexcomInbox`() {
        dataReceiver.processIntent(context, createIntent(Intents.DEXCOM_BG))
        verify(dataInbox).putAndEnqueue(eq(DexcomInbox), eq(bundle))
    }

    @Test
    fun `DEXCOM_G7_BG routes to DexcomInbox`() {
        dataReceiver.processIntent(context, createIntent(Intents.DEXCOM_G7_BG))
        verify(dataInbox).putAndEnqueue(eq(DexcomInbox), eq(bundle))
    }

    @Test
    fun `no bundle is a no-op`() {
        // Intent with no extras attached
        dataReceiver.processIntent(context, Intent(Intents.ACTION_NEW_BG_ESTIMATE))
        verify(dataInbox, never()).putAndEnqueue(any(), any())
    }

    @Test
    fun `unknown action does not touch the inbox`() {
        dataReceiver.processIntent(context, createIntent("some.unknown.ACTION"))
        verify(dataInbox, never()).putAndEnqueue(any(), any())
    }
}
