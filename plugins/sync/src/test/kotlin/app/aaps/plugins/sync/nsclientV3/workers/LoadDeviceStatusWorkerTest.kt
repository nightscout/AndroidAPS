package app.aaps.plugins.sync.nsclientV3.workers

import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclient.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
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

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var sut: LoadDeviceStatusWorker

    init {
        addInjector {
            if (it is LoadDeviceStatusWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.rxBus = rxBus
                it.context = context
                it.dateUtil = dateUtil
                it.nsClientV3Plugin = nsClientV3Plugin
                it.dataWorkerStorage = dataWorkerStorage
                it.nsDeviceStatusHandler = nsDeviceStatusHandler
            }
        }
    }

    @BeforeEach
    fun setUp() {
        dataWorkerStorage = DataWorkerStorage(context)
        receiverDelegate = ReceiverDelegate(rxBus, rh, preferences, receiverStatusStore, aapsSchedulers, fabricPrivacy)
        nsClientV3Plugin = NSClientV3Plugin(
            aapsLogger, rh, preferences, aapsSchedulers, rxBus, context, fabricPrivacy,
            receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
            nsClientSource, storeDataForDb, decimalFormatter, l
        )
        nsClientV3Plugin.newestDataOnServer = LastModified(LastModified.Collections())
    }

    @Test
    fun `notInitializedAndroidClient returns failure`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo("AndroidClient is null")
    }

    @Test
    fun `successful load with data returns success`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()

        val deviceStatus = NSDeviceStatus(
            date = now - 1000,
            device = "test-device",
            uploaderBattery = 80,
            isCharging = true,
            uploader = null,
            pump = null,
            openaps = null,
            configuration = null
        )
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(listOf(deviceStatus))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        org.mockito.kotlin.verify(nsDeviceStatusHandler).handleNewData(org.mockito.kotlin.any())
    }

    @Test
    fun `successful load with empty data returns success`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        org.mockito.kotlin.verify(nsDeviceStatusHandler, org.mockito.kotlin.never()).handleNewData(org.mockito.kotlin.any())
    }

    @Test
    fun `error handling returns failure and sets lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()
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
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastOperationError = "Previous error"
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun `sets initialLoadFinished to true`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.initialLoadFinished = false
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.initialLoadFinished).isTrue()
    }

    @Test
    fun `loads data from last 7 minutes`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        val expectedFrom = now - T.mins(7).msecs()
        org.mockito.kotlin.verify(nsAndroidClient).getDeviceStatusModifiedSince(expectedFrom)
    }

    @Test
    fun `handles multiple device statuses`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()

        val deviceStatus1 = NSDeviceStatus(
            date = now - 1000,
            device = "test-device-1",
            uploaderBattery = 80,
            isCharging = true,
            uploader = null,
            pump = null,
            openaps = null,
            configuration = null
        )
        val deviceStatus2 = NSDeviceStatus(
            date = now - 2000,
            device = "test-device-2",
            uploaderBattery = 75,
            isCharging = true,
            uploader = null,
            pump = null,
            openaps = null,
            configuration = null
        )
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(listOf(deviceStatus1, deviceStatus2))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        org.mockito.kotlin.verify(nsDeviceStatusHandler).handleNewData(argThat { size == 2 })
    }

    @Test
    fun `initialLoadFinished remains true once set`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.initialLoadFinished = true
        sut = TestListenableWorkerBuilder<LoadDeviceStatusWorker>(context).build()
        whenever(nsAndroidClient.getDeviceStatusModifiedSince(anyLong()))
            .thenReturn(emptyList())

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.initialLoadFinished).isTrue()
    }
}
