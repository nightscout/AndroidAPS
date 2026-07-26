package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.IDs
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.extensions.toNSCarbs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadTreatmentsWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var l: L
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var nsClientRepository: NSClientRepository
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var sut: LoadTreatmentsWorker

    private fun buildSut(): LoadTreatmentsWorker =
        TestListenableWorkerBuilder<LoadTreatmentsWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    LoadTreatmentsWorker(appContext, workerParameters, aapsLogger, fabricPrivacy, nsClientV3Plugin, dateUtil, storeDataForDb, nsIncomingDataProcessor, nsClientRepository)
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
    fun `first load with empty return updates lastLoadedSrvModified`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.treatments).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `first load with data returns success`() = runTest(timeout = 30.seconds) {
        val carbs = CA(
            timestamp = 10000,
            isValid = true,
            amount = 20.0,
            duration = 0,
            ids = IDs(nightscoutId = "nightscoutId")
        )

        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        sut = buildSut()
        val nsTreatment = carbs.toNSCarbs()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, listOf(nsTreatment)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `no load needed when server has no newer data`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = now - 2000
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.treatments).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `subsequent load with data updates lastLoadedSrvModified`() = runTest(timeout = 30.seconds) {
        val carbs = CA(
            timestamp = 10000,
            isValid = true,
            amount = 20.0,
            duration = 0,
            ids = IDs(nightscoutId = "nightscoutId")
        )

        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = now - 2000 // Not first load
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = now
        sut = buildSut()
        val nsTreatment = carbs.toNSCarbs()
        whenever(nsAndroidClient.getTreatmentsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, listOf(nsTreatment)))

        val result = sut.doWorkAndLog()

        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.treatments).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `subsequent load with empty return ends loading`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = now - 2000 // Not first load
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = now
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `error handling returns failure and sets lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = Long.MAX_VALUE
        sut = buildSut()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
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
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        nsClientV3Plugin.lastOperationError = "Previous error"
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun `storeTreatmentsToDb is called at the end`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        org.mockito.kotlin.verify(storeDataForDb).storeTreatmentsToDb(fullSync = false)
    }

    @Test
    fun `storeTreatmentsToDb is called with fullSync flag during full sync`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        nsClientV3Plugin.doingFullSync = true
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        org.mockito.kotlin.verify(storeDataForDb).storeTreatmentsToDb(fullSync = true)
    }

    @Test
    fun `304 response stops loading`() = runTest(timeout = 30.seconds) {
        val carbs = CA(
            timestamp = 10000,
            isValid = true,
            amount = 20.0,
            duration = 0,
            ids = IDs(nightscoutId = "nightscoutId")
        )

        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = now
        sut = buildSut()
        val nsTreatment = carbs.toNSCarbs()
        // 304 = Not Modified response
        whenever(nsAndroidClient.getTreatmentsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(304, now - 1000, listOf(nsTreatment)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        // Should only call once, not continue loading
        org.mockito.kotlin.verify(nsAndroidClient, org.mockito.kotlin.times(1)).getTreatmentsModifiedSince(anyLong(), anyInt())
    }

    @Test
    fun `first load uses getTreatmentsNewerThan`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.treatments = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = Long.MAX_VALUE
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsNewerThan(anyString(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        org.mockito.kotlin.verify(nsAndroidClient).getTreatmentsNewerThan(anyString(), anyInt())
        org.mockito.kotlin.verify(nsAndroidClient, org.mockito.kotlin.never()).getTreatmentsModifiedSince(anyLong(), anyInt())
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `subsequent load uses getTreatmentsModifiedSince`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.treatments = now - 2000 // Not first load
        nsClientV3Plugin.newestDataOnServer?.collections?.treatments = now
        sut = buildSut()
        whenever(nsAndroidClient.getTreatmentsModifiedSince(anyLong(), anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, emptyList()))

        val result = sut.doWorkAndLog()

        org.mockito.kotlin.verify(nsAndroidClient).getTreatmentsModifiedSince(anyLong(), anyInt())
        org.mockito.kotlin.verify(nsAndroidClient, org.mockito.kotlin.never()).getTreatmentsNewerThan(anyString(), anyInt())
        assertIs<ListenableWorker.Result.Success>(result)
    }
}
