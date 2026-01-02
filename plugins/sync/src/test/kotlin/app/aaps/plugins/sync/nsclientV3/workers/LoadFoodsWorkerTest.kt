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
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class LoadFoodsWorkerTest : TestBaseWithProfile() {

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
    private lateinit var sut: LoadFoodsWorker

    init {
        addInjector {
            if (it is LoadFoodsWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.rxBus = rxBus
                it.context = context
                it.dateUtil = dateUtil
                it.nsClientV3Plugin = nsClientV3Plugin
                it.storeDataForDb = storeDataForDb
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
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        assertThat(result.outputData.getString("Error")).isEqualTo("AndroidClient is null")
    }

    @Test
    fun `loads foods on 5th attempt`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 5 // Next increment will be 5, which % 5 == 0
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()

        val food = NSFood(
            name = "Apple",
            category = "Fruit",
            subCategory = "Fresh",
            portion = 100.0,
            carbs = 13,
            fat = 0,
            protein = 0,
            energy = 52,
            unit = "g",
            gi = null,
            date = now,
            identifier = "some",
            isValid = true
        )
        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, listOf(food)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsAndroidClient).getFoods(1000)
        verify(nsIncomingDataProcessor).processFood(any())
        verify(storeDataForDb).storeFoodsToDb()
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.foods).isEqualTo(6)
    }

    @Test
    fun `loads on every 5th attempt - counter 0`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 0 // 0 % 5 == 0
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()

        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsAndroidClient).getFoods(1000)
    }

    @Test
    fun `loads on every 5th attempt - counter 9`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 9 // Next increment will be 10, which % 5 == 0
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()

        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsAndroidClient, never()).getFoods(1000)
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.foods).isEqualTo(10)
    }

    @Test
    fun `error handling returns failure and sets lastOperationError`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 5
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()
        val errorMessage = "Network error"
        whenever(nsAndroidClient.getFoods(anyInt()))
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
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 4
        nsClientV3Plugin.lastOperationError = "Previous error"
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()
        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(nsClientV3Plugin.lastOperationError).isNull()
    }

    @Test
    fun `loads empty food list`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 5
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()
        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(storeDataForDb).storeFoodsToDb()
    }

    @Test
    fun `loads multiple foods`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 5
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()

        val food1 = NSFood(
            name = "Apple",
            category = "Fruit",
            subCategory = "Fresh",
            portion = 100.0,
            carbs = 13,
            fat = 0,
            protein = 0,
            energy = 52,
            unit = "g",
            gi = null,
            date = now,
            identifier = "some",
            isValid = true
        )
        val food2 = NSFood(
            name = "Banana",
            category = "Fruit",
            subCategory = "Fresh",
            portion = 100.0,
            carbs = 23,
            fat = 0,
            protein = 1,
            energy = 89,
            unit = "g",
            gi = null,
            date = now,
            identifier = "some",
            isValid = true
        )
        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, listOf(food1, food2)))

        val result = sut.doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(nsIncomingDataProcessor).processFood(argThat { (this as List<*>).size == 2 })
    }

    @Test
    fun `requests 1000 foods maximum`() = runTest(timeout = 30.seconds) {
        whenever(workManager.beginUniqueWork(anyString(), anyOrNull(), anyOrNull<OneTimeWorkRequest>())).thenReturn(workContinuation)
        whenever(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.foods = 5
        sut = TestListenableWorkerBuilder<LoadFoodsWorker>(context).build()
        whenever(nsAndroidClient.getFoods(anyInt()))
            .thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        sut.doWorkAndLog()

        verify(nsAndroidClient).getFoods(1000)
    }
}
