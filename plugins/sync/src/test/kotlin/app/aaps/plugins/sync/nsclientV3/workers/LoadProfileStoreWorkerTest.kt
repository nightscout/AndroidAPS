package app.aaps.plugins.sync.nsclientV3.workers

import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadProfileStoreWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var l: L
    @Mock lateinit var nsClientSource: NSClientSource

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var sut: LoadProfileStoreWorker

    init {
        addInjector {
            if (it is LoadProfileStoreWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.rxBus = rxBus
                it.context = context
                it.dateUtil = dateUtil
                it.nsClientV3Plugin = nsClientV3Plugin
                it.dataWorkerStorage = dataWorkerStorage
                it.nsIncomingDataProcessor = nsIncomingDataProcessor
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
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo("AndroidClient is null")
    }

    @Test
    fun `first load uses getLastProfileStore`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = 0L // First load
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = Long.MAX_VALUE
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val profile = JSONObject().apply {
            put("defaultProfile", "Default")
            put("store", JSONObject())
            put("srvModified", now - 1000)
        }
        whenever(nsAndroidClient.getLastProfileStore())
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, listOf(profile)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsAndroidClient).getLastProfileStore()
        verify(nsAndroidClient, never()).getProfileModifiedSince(anyLong())
    }

    @Test
    fun `subsequent load uses getProfileModifiedSince`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000 // Not first load
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val profile = JSONObject().apply {
            put("defaultProfile", "Default")
            put("store", JSONObject())
        }
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, listOf(profile)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsAndroidClient).getProfileModifiedSince(anyLong())
        verify(nsAndroidClient, never()).getLastProfileStore()
    }

    @Test
    fun `updates lastLoadedSrvModified from response`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val profile = JSONObject().apply {
            put("defaultProfile", "Default")
            put("store", JSONObject())
        }
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, listOf(profile)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.profile).isEqualTo(now - 1000)
    }

    @Test
    fun `updates lastLoadedSrvModified from record when not in response`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val profile = JSONObject().apply {
            put("defaultProfile", "Default")
            put("store", JSONObject())
            put("srvModified", now - 500)
        }
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, null, listOf(profile)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.profile).isEqualTo(now - 500)
    }

    @Test
    fun `updates lastLoadedSrvModified from created_at when srvModified not available`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val createdAt = dateUtil.toISOString(now - 300)
        val profile = JSONObject().apply {
            put("defaultProfile", "Default")
            put("store", JSONObject())
            put("created_at", createdAt)
        }
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, null, listOf(profile)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.profile).isEqualTo(now - 300)
    }

    @Test
    fun `no load when server has no newer data`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now - 2000 // Older than lastLoaded
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsAndroidClient, never()).getLastProfileStore()
        verify(nsAndroidClient, never()).getProfileModifiedSince(anyLong())
    }

    @Test
    fun `handles empty profile list`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, null, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsIncomingDataProcessor, never()).processProfile(org.mockito.kotlin.any(), anyBoolean())
    }

    @Test
    fun `processes only last profile when multiple returned`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val profile1 = JSONObject().apply {
            put("defaultProfile", "Profile1")
            put("store", JSONObject())
            put("srvModified", now - 1000)
        }
        val profile2 = JSONObject().apply {
            put("defaultProfile", "Profile2")
            put("store", JSONObject())
            put("srvModified", now - 500)
        }
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 500, listOf(profile1, profile2)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsIncomingDataProcessor).processProfile(
            org.mockito.kotlin.argThat { getString("defaultProfile") == "Profile2" },
            anyBoolean()
        )
    }

    @Test
    fun `error handling returns failure`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenThrow(RuntimeException(errorMessage))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo(errorMessage)
    }

    @Test
    fun `passes doingFullSync flag to processProfile`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.profile = now - 2000
        nsClientV3Plugin.newestDataOnServer?.collections?.profile = now
        nsClientV3Plugin.doingFullSync = true
        sut = TestListenableWorkerBuilder<LoadProfileStoreWorker>(context).build()

        val profile = JSONObject().apply {
            put("defaultProfile", "Default")
            put("store", JSONObject())
        }
        whenever(nsAndroidClient.getProfileModifiedSince(anyLong()))
            .thenReturn(NSAndroidClient.ReadResponse(200, now - 1000, listOf(profile)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsIncomingDataProcessor).processProfile(org.mockito.kotlin.any(), org.mockito.kotlin.eq(true))
    }
}
