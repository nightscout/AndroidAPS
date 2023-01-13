package info.nightscout.plugins.sync.nsclientV3.workers

import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.interfaces.source.NSClientSource
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsclient.NsClientReceiverDelegate
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSSvgV3
import info.nightscout.rx.bus.RxBus
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoadBgWorkerTest : TestBase() {

    @Mock lateinit var workerClasses: WorkerClasses
    @Mock lateinit var sp: SP
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var dataSyncSelector: DataSyncSelector
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var nsClientReceiverDelegate: NsClientReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var sut: LoadBgWorker

    private val now = 1000000000L

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is LoadBgWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.dataWorkerStorage = dataWorkerStorage
                it.sp = sp
                it.rxBus = rxBus
                it.context = context
                it.dateUtil = dateUtil
                it.nsClientV3Plugin = nsClientV3Plugin
                it.workerClasses = workerClasses
                it.nsClientSource = nsClientSource
                it.workManager = workManager
            }
        }
    }

    @BeforeEach
    fun setUp() {
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.androidInjector()).thenReturn(injector.androidInjector())
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        Mockito.`when`(nsClientSource.isEnabled()).thenReturn(true)
        dataWorkerStorage = DataWorkerStorage(context)
        nsClientReceiverDelegate = NsClientReceiverDelegate(rxBus, rh, sp, receiverStatusStore)
        nsClientV3Plugin = NSClientV3Plugin(
            injector, aapsLogger, aapsSchedulers, rxBus, rh, context, fabricPrivacy, sp, nsClientReceiverDelegate, config, dateUtil, uiInteraction, dataSyncSelector,
            profileFunction, repository
        )
        nsClientV3Plugin.newestDataOnServer = LastModified(LastModified.Collections())
    }

    @Test
    fun notInitializedAndroidClient() = runTest {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()

        val result = sut.doWorkAndLog()
        Assertions.assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun notEnabledNSClientSource() = runTest {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsClientSource.isEnabled()).thenReturn(false)
        Mockito.`when`(sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_cgm, false)).thenReturn(false)

        val result = sut.doWorkAndLog()
        Assertions.assertTrue(result is ListenableWorker.Result.Success)
        Assertions.assertTrue(result.outputData.getString("Result") == "Load not enabled")
        Mockito.verify(workManager, Mockito.times(1)).enqueueUniqueWork(
            eq(NSClientV3Plugin.JOB_NAME),
            eq(ExistingWorkPolicy.APPEND_OR_REPLACE),
            any<OneTimeWorkRequest>()
        )
    }

    @Test
    fun testThereAreNewerDataFirstLoadEmptyReturn() = runTest {
        Mockito.`when`(workManager.beginUniqueWork(anyString(), any(), any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        Mockito.`when`(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyLong())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        Assertions.assertEquals(now - 1000, nsClientV3Plugin.lastLoadedSrvModified.collections.entries)
        Assertions.assertTrue(result is ListenableWorker.Result.Success)
        Mockito.verify(workManager, Mockito.times(1)).beginUniqueWork(
            eq(NSClientV3Plugin.JOB_NAME),
            eq(ExistingWorkPolicy.APPEND_OR_REPLACE),
            any<OneTimeWorkRequest>()
        )
        Mockito.verify(workContinuation, Mockito.times(1)).then(any<OneTimeWorkRequest>())
        Mockito.verify(workContinuation, Mockito.times(1)).enqueue()
    }

    @Test
    fun testThereAreNewerDataFirstLoadListReturn() = runTest {

        val glucoseValue = GlucoseValue(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = GlucoseValue.TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId"
            )
        )

        Mockito.`when`(workManager.beginUniqueWork(anyString(), any(), any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        Mockito.`when`(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        Mockito.`when`(workerClasses.nsClientSourceWorker).thenReturn(LoggingWorker::class.java)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyLong())).thenReturn(NSAndroidClient.ReadResponse(200, 0, listOf(glucoseValue.toNSSvgV3())))

        val result = sut.doWorkAndLog()
        Assertions.assertTrue(result is ListenableWorker.Result.Success)
        Mockito.verify(workManager, Mockito.times(1)).beginUniqueWork(
            eq(NSClientV3Plugin.JOB_NAME),
            eq(ExistingWorkPolicy.APPEND_OR_REPLACE),
            any<OneTimeWorkRequest>()
        )
        Mockito.verify(workContinuation, Mockito.times(1)).then(any<OneTimeWorkRequest>())
        Mockito.verify(workContinuation, Mockito.times(1)).enqueue()
    }


    @Test
    fun testNoLoadNeeded() = runTest {
        Mockito.`when`(workManager.beginUniqueWork(anyString(), any(), any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        Mockito.`when`(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = now - 2000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyLong())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        Assertions.assertEquals(now - 1000, nsClientV3Plugin.lastLoadedSrvModified.collections.entries)
        Assertions.assertTrue(result is ListenableWorker.Result.Success)
        Mockito.verify(workManager, Mockito.times(1)).beginUniqueWork(
            eq(NSClientV3Plugin.JOB_NAME),
            eq(ExistingWorkPolicy.APPEND_OR_REPLACE),
            any<OneTimeWorkRequest>()
        )
        Mockito.verify(workContinuation, Mockito.times(1)).then(any<OneTimeWorkRequest>())
        Mockito.verify(workContinuation, Mockito.times(1)).enqueue()
    }
}