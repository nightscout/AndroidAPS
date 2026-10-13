package app.aaps.plugins.sync.nsclientV3.workers

import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.extensions.toNSSvgV3
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadBgWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var l: L
    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var sut: LoadBgWorker

    init {
        addInjector {
            if (it is LoadBgWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.preferences = preferences
                it.rxBus = rxBus
                it.context = context
                it.dateUtil = dateUtil
                it.nsClientV3Plugin = nsClientV3Plugin
                it.nsClientSource = nsClientSource
                it.storeDataForDb = storeDataForDb
                it.nsIncomingDataProcessor = nsIncomingDataProcessor
            }
        }
    }

    @BeforeEach
    fun setUp() {
        whenever(nsClientSource.isEnabled()).thenReturn(true)
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
    fun notInitializedAndroidClient() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()

        val result = sut.doWorkAndLog()
        assertIs<ListenableWorker.Result.Failure>(result)
    }

    @Test
    fun notEnabledNSClientSource() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsClientSource.isEnabled()).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientAcceptCgmData)).thenReturn(false)

        val result = sut.doWorkAndLog()
        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(result.outputData.getString("Result")).isEqualTo("Load not enabled")
    }

    @Test
    fun testThereAreNewerDataFirstLoadEmptyReturn() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.entries).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun testThereAreNewerDataFirstLoadListReturn() = runTest(timeout = 30.seconds) {

        val glucoseValue = GV(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
            ids = IDs(
                nightscoutId = "nightscoutId"
            )
        )

        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt())).thenReturn(NSAndroidClient.ReadResponse(200, 0, listOf(glucoseValue.toNSSvgV3())))

        val result = sut.doWorkAndLog()
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun testNoLoadNeeded() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = now - 2000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.entries).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun testSubsequentLoadWithData() = runTest(timeout = 30.seconds) {
        val glucoseValue = GV(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
            ids = IDs(nightscoutId = "nightscoutId")
        )

        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = now - 2000 // Not first load
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = now
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, listOf(glucoseValue.toNSSvgV3())))

        val result = sut.doWorkAndLog()

        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.entries).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun testSubsequentLoadWithEmptyReturn() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = now - 2000 // Not first load
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = now
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun testErrorHandling() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = Long.MAX_VALUE
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt()))
            .thenThrow(RuntimeException(errorMessage))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo(errorMessage)
        assertThat(nsClientV3Plugin.lastOperationError).isEqualTo(errorMessage)
    }

    @Test
    fun testSuccessfulLoadClearsLastOperationError() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        nsClientV3Plugin.lastOperationError = "Previous error"
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun testLoadEnabledWhenAcceptCgmDataIsTrue() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsClientSource.isEnabled()).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientAcceptCgmData)).thenReturn(true)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        // Should not return early with "Load not enabled"
        assertThat(result.outputData.getString("Result")).isNull()
    }

    @Test
    fun testLoadEnabledDuringFullSync() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsClientSource.isEnabled()).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientAcceptCgmData)).thenReturn(false)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        nsClientV3Plugin.doingFullSync = true
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        // Should not return early with "Load not enabled"
        assertThat(result.outputData.getString("Result")).isNull()
    }

    @Test
    fun testStoreGlucoseValuesToDbIsCalled() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        whenever(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        org.mockito.kotlin.verify(storeDataForDb).storeGlucoseValuesToDb()
    }

    @Test
    fun test304ResponseStopsLoading() = runTest(timeout = 30.seconds) {
        val glucoseValue = GV(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
            ids = IDs(nightscoutId = "nightscoutId")
        )

        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = now
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        // 304 = Not Modified response
        whenever(nsAndroidClient.getSgvsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(304, now - 1000, listOf(glucoseValue.toNSSvgV3())))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        // Should only call once, not continue loading
        org.mockito.kotlin.verify(nsAndroidClient, org.mockito.kotlin.times(1)).getSgvsModifiedSince(anyLong(), anyInt())
    }
}
