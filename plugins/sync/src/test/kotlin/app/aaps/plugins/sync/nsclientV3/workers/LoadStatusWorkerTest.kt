package app.aaps.plugins.sync.nsclientV3.workers

import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.ApiPermission
import app.aaps.core.nssdk.localmodel.ApiPermissions
import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.localmodel.Storage
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsShared.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadStatusWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var l: L
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var nsClientMvvmRepository: NSClientRepositoryImpl
    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var sut: LoadStatusWorker

    init {
        addInjector {
            if (it is LoadStatusWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.nsClientV3Plugin = nsClientV3Plugin
                it.nsClientRepository = nsClientMvvmRepository
            }
        }
    }

    @BeforeEach
    fun setUp() {
        nsClientMvvmRepository = NSClientRepositoryImpl(rxBus, aapsLogger)
        dataWorkerStorage = DataWorkerStorage(context)
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeAnyChange()).thenReturn(emptyFlow())
        whenever(receiverStatusStore.networkStatusFlow).thenReturn(MutableStateFlow(null))
        whenever(receiverStatusStore.chargingStatusFlow).thenReturn(MutableStateFlow(null))
        receiverDelegate = ReceiverDelegate(rh, preferences, receiverStatusStore)
        nsClientV3Plugin = NSClientV3Plugin(
            aapsLogger, rh, preferences, rxBus, context,
            receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
            nsClientSource, storeDataForDb, decimalFormatter, l, nsClientMvvmRepository, uel, activePlugin
        )
        nsClientV3Plugin.newestDataOnServer = LastModified(LastModified.Collections())
    }

    @Test
    fun `notInitializedAndroidClient returns failure`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<LoadStatusWorker>(context).build()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo("AndroidClient is null")
    }

    @Test
    fun `successful load returns success`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadStatusWorker>(context).build()

        val status = Status(
            version = "15.0.0",
            apiVersion = "1.0",
            srvDate = now,
            storage = Storage(storage = "storage", version = "1.0"),
            apiPermissions = ApiPermissions(
                deviceStatus = ApiPermission(create = true, read = true, update = true, delete = true),
                entries = ApiPermission(create = true, read = true, update = true, delete = true),
                food = ApiPermission(create = true, read = true, update = true, delete = true),
                profile = ApiPermission(create = true, read = true, update = true, delete = true),
                settings = ApiPermission(create = true, read = true, update = true, delete = true),
                treatments = ApiPermission(create = true, read = true, update = true, delete = true)
            )
        )
        whenever(nsAndroidClient.getStatus()).thenReturn(status)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun `error handling returns failure and sets lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadStatusWorker>(context).build()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getStatus())
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
        sut = TestListenableWorkerBuilder<LoadStatusWorker>(context).build()

        val status = Status(
            version = "15.0.0",
            apiVersion = "1.0",
            srvDate = now,
            storage = Storage(storage = "storage", version = "1.0"),
            apiPermissions = ApiPermissions(
                deviceStatus = ApiPermission(create = true, read = true, update = true, delete = true),
                entries = ApiPermission(create = true, read = true, update = true, delete = true),
                food = ApiPermission(create = true, read = true, update = true, delete = true),
                profile = ApiPermission(create = true, read = true, update = true, delete = true),
                settings = ApiPermission(create = true, read = true, update = true, delete = true),
                treatments = ApiPermission(create = true, read = true, update = true, delete = true)
            )
        )
        whenever(nsAndroidClient.getStatus()).thenReturn(status)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun `sends error log event on failure`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadStatusWorker>(context).build()

        val errorMessage = "Connection timeout"
        whenever(nsAndroidClient.getStatus())
            .thenThrow(RuntimeException(errorMessage))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "◄ ERROR" && it.logText == errorMessage }).isTrue()
    }

    @Test
    fun `handles different status responses`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(org.mockito.kotlin.any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        sut = TestListenableWorkerBuilder<LoadStatusWorker>(context).build()

        val status = Status(
            version = "14.2.6",
            apiVersion = "1.0",
            srvDate = now,
            storage = Storage(storage = "storage", version = "1.0"),
            apiPermissions = ApiPermissions(
                deviceStatus = ApiPermission(create = true, read = true, update = true, delete = true),
                entries = ApiPermission(create = true, read = true, update = true, delete = true),
                food = ApiPermission(create = true, read = true, update = true, delete = true),
                profile = ApiPermission(create = true, read = true, update = true, delete = true),
                settings = ApiPermission(create = true, read = true, update = true, delete = true),
                treatments = ApiPermission(create = true, read = true, update = true, delete = true)
            )
        )
        whenever(nsAndroidClient.getStatus()).thenReturn(status)

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
    }
}
