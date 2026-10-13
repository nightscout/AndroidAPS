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
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadLastModificationWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var l: L
    @Mock lateinit var nsClientSource: NSClientSource

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var sut: LoadLastModificationWorker

    init {
        addInjector {
            if (it is LoadLastModificationWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.rxBus = rxBus
                it.nsClientV3Plugin = nsClientV3Plugin
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
        nsClientV3Plugin.newestDataOnServer = null
    }

    @Test
    fun `notInitializedAndroidClient returns failure`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo("AndroidClient is null")
    }

    @Test
    fun `successful load updates newestDataOnServer`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()

        val lastModified = LastModified(
            LastModified.Collections(
                entries = now - 1000,
                treatments = now - 2000,
                devicestatus = now - 3000,
                profile = now - 4000,
                foods = 5
            )
        )
        whenever(nsAndroidClient.getLastModified()).thenReturn(lastModified)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.newestDataOnServer).isEqualTo(lastModified)
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.entries).isEqualTo(now - 1000)
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.treatments).isEqualTo(now - 2000)
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.devicestatus).isEqualTo(now - 3000)
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.profile).isEqualTo(now - 4000)
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.foods).isEqualTo(5)
    }

    @Test
    fun `error handling returns failure and sets lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getLastModified())
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
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()

        val lastModified = LastModified(LastModified.Collections())
        whenever(nsAndroidClient.getLastModified()).thenReturn(lastModified)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun `updates newestDataOnServer when previously null`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.newestDataOnServer = null
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()

        val lastModified = LastModified(LastModified.Collections(entries = now))
        whenever(nsAndroidClient.getLastModified()).thenReturn(lastModified)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.newestDataOnServer).isNotNull()
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.entries).isEqualTo(now)
    }

    @Test
    fun `overwrites existing newestDataOnServer`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.newestDataOnServer = LastModified(LastModified.Collections(entries = now - 10000))
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()

        val newLastModified = LastModified(LastModified.Collections(entries = now))
        whenever(nsAndroidClient.getLastModified()).thenReturn(newLastModified)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.newestDataOnServer?.collections?.entries).isEqualTo(now)
    }

    @Test
    fun `handles empty LastModified collections`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadLastModificationWorker>(context).build()

        val lastModified = LastModified(LastModified.Collections())
        whenever(nsAndroidClient.getLastModified()).thenReturn(lastModified)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.newestDataOnServer).isNotNull()
    }
}
