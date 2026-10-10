package app.aaps.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.plugins.source.AidexPlugin
import app.aaps.plugins.source.DexcomInbox
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripInbox
import app.aaps.plugins.source.instara.InstaraPlugin
import app.aaps.plugins.sync.smsCommunicator.SmsInbox
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    @Mock private lateinit var workManager: WorkManager
    @Mock private lateinit var context: Context
    @Mock private lateinit var bundle: Bundle

    @BeforeEach
    fun setUp() {
        // Manually inject mocks into the receiver instance
        dataReceiver = DataReceiver().also {
            it.aapsLogger = aapsLogger
            it.dataInbox = dataInbox
            it.fabricPrivacy = fabricPrivacy
            it.workManager = workManager
        }
    }

    private fun createIntent(action: String): Intent {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(action)
        whenever(intent.extras).thenReturn(bundle)
        return intent
    }

    /** Asserts the given action enqueues a unique "data" work request for [workerClass]. */
    @Suppress("RestrictedApi")
    private fun assertRoutesToWorker(action: String, workerClass: Class<out ListenableWorker>) {
        dataReceiver.processIntent(context, createIntent(action))

        val captor = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(eq("data"), eq(ExistingWorkPolicy.APPEND_OR_REPLACE), captor.capture())
        assertThat(captor.firstValue.workSpec.workerClassName).isEqualTo(workerClass.name)
    }

    // ---- Inbox routing ----

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

    // ---- Inline-Data worker routing ----

    @ParameterizedTest(name = "{0} routes to {1}")
    @MethodSource("inlineRoutes")
    fun `inline source action routes to the correct worker`(action: String, workerClass: Class<out ListenableWorker>) {
        assertRoutesToWorker(action, workerClass)
    }

    @Test
    fun `inline source action does not touch the inbox`() {
        dataReceiver.processIntent(context, createIntent(Intents.POCTECH_BG))
        verify(dataInbox, never()).putAndEnqueue(any(), any())
    }

    @Test
    fun `inbox action does not enqueue inline work`() {
        dataReceiver.processIntent(context, createIntent(Intents.ACTION_NEW_BG_ESTIMATE))
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    // ---- Guard rails ----

    @Test
    fun `no bundle is a no-op`() {
        // Intent with no extras attached
        dataReceiver.processIntent(context, Intent(Intents.ACTION_NEW_BG_ESTIMATE))
        verify(dataInbox, never()).putAndEnqueue(any(), any())
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `unknown action does not touch the inbox or enqueue work`() {
        dataReceiver.processIntent(context, createIntent("some.unknown.ACTION"))
        verify(dataInbox, never()).putAndEnqueue(any(), any())
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    companion object {

        @JvmStatic
        fun inlineRoutes(): List<Arguments> = listOf(
            Arguments.of(Intents.POCTECH_BG, PoctechPlugin.PoctechWorker::class.java),
            Arguments.of(Intents.GLIMP_BG, GlimpPlugin.GlimpWorker::class.java),
            Arguments.of(Intents.TOMATO_BG, TomatoPlugin.TomatoWorker::class.java),
            Arguments.of(Intents.NS_EMULATOR, MM640gPlugin.MM640gWorker::class.java),
            Arguments.of(Intents.OTTAI_APP, SyaiPlugin.SyaiWorker::class.java),
            Arguments.of(Intents.OTTAI_APP_CN, SyaiPlugin.SyaiWorker::class.java),
            Arguments.of(Intents.SYAI_APP, SyaiPlugin.SyaiWorker::class.java),
            Arguments.of(Intents.SI_APP, PatchedSiAppPlugin.PatchedSiAppWorker::class.java),
            Arguments.of(Intents.SINO_APP, PatchedSinoAppPlugin.PatchedSinoAppWorker::class.java),
            Arguments.of(Intents.INSTARA_APP, InstaraPlugin.InstaraWorker::class.java),
            Arguments.of(Intents.AIDEX_NEW_BG_ESTIMATE, AidexPlugin.AidexWorker::class.java),
        )
    }
}
