package app.aaps.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.work.OneTimeWorkRequest
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

class DataReceiverTest : TestBase() {

    // The System Under Test
    private lateinit var dataReceiver: DataReceiver

    // Mocks for dependencies
    @Mock private lateinit var dataWorkerStorage: DataWorkerStorage
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var context: Context
    @Mock private lateinit var bundle: Bundle

    private val workRequestCaptor = argumentCaptor<OneTimeWorkRequest>()

    @BeforeEach
    fun setUp() {

        // Manually inject mocks into the receiver instance
        dataReceiver = DataReceiver().also {
            it.aapsLogger = aapsLogger
            it.dataWorkerStorage = dataWorkerStorage
            it.fabricPrivacy = fabricPrivacy
        }
    }

    private fun createIntent(action: String): Intent {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(action)
        whenever(intent.extras).thenReturn(bundle)
        return intent
    }

    private fun assertWorkerEnqueued(workerClass: KClass<*>) {
        verify(dataWorkerStorage).enqueue(workRequestCaptor.capture())
        val capturedRequest = workRequestCaptor.singleValue
        assert(capturedRequest.workSpec.workerClassName == workerClass.java.name)
    }

    @Test
    fun `processIntent enqueues XdripSourceWorker for ACTION_NEW_BG_ESTIMATE`() {
        // Arrange
        val intent = createIntent(Intents.ACTION_NEW_BG_ESTIMATE)
        whenever(dataWorkerStorage.storeInputData(any(), any())).thenReturn(androidx.work.Data.EMPTY)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(XdripSourcePlugin.XdripSourceWorker::class)
    }

    @Test
    fun `processIntent enqueues PoctechWorker for POCTECH_BG`() {
        // Arrange
        val intent = createIntent(Intents.POCTECH_BG)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(PoctechPlugin.PoctechWorker::class)
    }

    @Test
    fun `processIntent enqueues GlimpWorker for GLIMP_BG`() {
        // Arrange
        val intent = createIntent(Intents.GLIMP_BG)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(GlimpPlugin.GlimpWorker::class)
    }

    @Test
    fun `processIntent enqueues TomatoWorker for TOMATO_BG`() {
        // Arrange
        val intent = createIntent(Intents.TOMATO_BG)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(TomatoPlugin.TomatoWorker::class)
    }

    @Test
    fun `processIntent enqueues MM640gWorker for NS_EMULATOR`() {
        // Arrange
        val intent = createIntent(Intents.NS_EMULATOR)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(MM640gPlugin.MM640gWorker::class)
    }

    @Test
    fun `processIntent enqueues OttaiWorker for OTTAI_APP`() {
        // Arrange
        val intent = createIntent(Intents.OTTAI_APP)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(SyaiPlugin.SyaiWorker::class)
    }

    @Test
    fun `processIntent enqueues SyaiWorker for SYAI_APP`() {
        // Arrange
        val intent = createIntent(Intents.SYAI_APP)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(SyaiPlugin.SyaiWorker::class)
    }

    @Test
    fun `processIntent enqueues PatchedSiAppWorker for SI_APP`() {
        // Arrange
        val intent = createIntent(Intents.SI_APP)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(PatchedSiAppPlugin.PatchedSiAppWorker::class)
    }

    @Test
    fun `processIntent enqueues PatchedSinoAppWorker for SINO_APP`() {
        // Arrange
        val intent = createIntent(Intents.SINO_APP)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(PatchedSinoAppPlugin.PatchedSinoAppWorker::class)
    }

    @Test
    fun `processIntent enqueues SmsCommunicatorWorker for SMS_RECEIVED_ACTION`() {
        // Arrange
        val intent = createIntent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        whenever(dataWorkerStorage.storeInputData(any(), any())).thenReturn(androidx.work.Data.EMPTY)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(SmsCommunicatorPlugin.SmsCommunicatorWorker::class)
    }

    @Test
    fun `processIntent enqueues DexcomWorker for DEXCOM_BG`() {
        // Arrange
        val intent = createIntent(Intents.DEXCOM_BG)
        whenever(dataWorkerStorage.storeInputData(any(), any())).thenReturn(androidx.work.Data.EMPTY)

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        assertWorkerEnqueued(DexcomPlugin.DexcomWorker::class)
    }

    @Test
    fun `processIntent does nothing if intent has no bundle`() {
        // Arrange
        val intent = Intent(Intents.ACTION_NEW_BG_ESTIMATE) // No bundle attached

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        verify(dataWorkerStorage, never()).enqueue(any())
    }

    @Test
    fun `processIntent does nothing for an unknown action`() {
        // Arrange
        val intent = createIntent("some.unknown.ACTION")

        // Act
        dataReceiver.processIntent(context, intent)

        // Assert
        verify(dataWorkerStorage, never()).enqueue(any())
    }
}
