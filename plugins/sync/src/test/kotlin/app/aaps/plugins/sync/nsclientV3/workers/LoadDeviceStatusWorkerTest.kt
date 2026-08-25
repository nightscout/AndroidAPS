package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadDeviceStatusWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var l: L
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsClientRepository: NSClientRepository
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var sut: LoadDeviceStatusWorker

    private fun buildSut(): LoadDeviceStatusWorker =
        TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    LoadDeviceStatusWorker(appContext, workerParameters, aapsLogger, fabricPrivacy, nsClientV3Plugin, dateUtil, nsDeviceStatusHandler, nsClientRepository)
            })
            .build()

    @BeforeEach
    fun setUp() {
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeAnyChange()).thenReturn(emptyFlow())
        whenever(receiverStatusStore.networkStatusFlow).thenReturn(MutableStateFlow(null))
        whenever(receiverStatusStore.chargingStatusFlow).thenReturn(MutableStateFlow(null))
        receiverDelegate = ReceiverDelegate(rh, preferences, receiverStatusStore)
        nsClientV3Plugin = NSClientV3Plugin(
            aapsLogger, rh, preferences, rxBus, context,
            receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
            nsClientSource, storeDataForDb, decimalFormatter, l, nsClientRepository, uel, mock(), mock(), mock(), mock(), mock(), mock(), profileRepository
        )
        nsClientV3Plugin.newestDataOnServer = LastModified(LastModified.Collections())
    }

    @Test
    fun `notInitializedAndroidClient returns failure`() = runTest(timeout = 30.seconds) {
        sut = buildSut()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo("AndroidClient is null")
    }

    @Test
    fun `successful load with data returns success`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = buildSut()

        val deviceStatus = NSDeviceStatus(
            date = now - 1000,
            device = "test-device",
            uploaderBattery = 80,
            isCharging = true,
            uploader = null,
            pump = null,
            openaps = null,
        )
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(listOf(deviceStatus))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsDeviceStatusHandler).handleNewData(any(), any())
    }

    @Test
    fun `successful load with empty data returns success`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = buildSut()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsDeviceStatusHandler, never()).handleNewData(any(), any())
    }

    @Test
    fun `error handling returns failure and sets lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = buildSut()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenThrow(RuntimeException(errorMessage))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo(errorMessage)
        assertThat(nsClientV3Plugin.lastOperationError).isEqualTo(errorMessage)
    }

    @Test
    fun `successful load clears lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastOperationError = "Previous error"
        sut = buildSut()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun `sets initialLoadFinished to true`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.initialLoadFinished = false
        sut = buildSut()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.initialLoadFinished).isTrue()
    }

    @Test
    fun `loads data from last 7 minutes`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = buildSut()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        val expectedFrom = now - T.mins(7).msecs()
        verify(nsAndroidClient).getDeviceStatusModifiedSince(expectedFrom)
    }

    @Test
    fun `handles multiple device statuses`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = buildSut()

        val deviceStatus1 = NSDeviceStatus(
            date = now - 1000,
            device = "test-device-1",
            uploaderBattery = 80,
            isCharging = true,
            uploader = null,
            pump = null,
            openaps = null,
        )
        val deviceStatus2 = NSDeviceStatus(
            date = now - 2000,
            device = "test-device-2",
            uploaderBattery = 75,
            isCharging = true,
            uploader = null,
            pump = null,
            openaps = null,
        )
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(listOf(deviceStatus1, deviceStatus2))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        // live = false: the catch-up worker load must NOT bump the master-alive heartbeat (only live WS pushes do).
        verify(nsDeviceStatusHandler).handleNewData(argThat { size == 2 }, eq(false))
    }

    @Test
    fun `initialLoadFinished remains true once set`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.initialLoadFinished = true
        sut = buildSut()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.initialLoadFinished).isTrue()
    }
}
