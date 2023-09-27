package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclient.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.extensions.toNSSvgV3
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import kotlin.test.assertIs

internal class LoadBgWorkerTest : TestBase() {

    abstract class ContextWithInjector : Context(), HasAndroidInjector

    @Mock lateinit var sp: SP
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Config
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var workContinuation: WorkContinuation
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var context: ContextWithInjector

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var receiverDelegate: ReceiverDelegate
    private lateinit var dataWorkerStorage: DataWorkerStorage
    private lateinit var decimalFormatter: DecimalFormatter
    private lateinit var sut: LoadBgWorker

    private val now = 1000000000L

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is LoadBgWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.sp = sp
                it.rxBus = rxBus
                it.context = context
                it.dateUtil = dateUtil
                it.nsClientV3Plugin = nsClientV3Plugin
                it.nsClientSource = nsClientSource
                it.storeDataForDb = storeDataForDb
            }
        }
    }

    @BeforeEach
    fun setUp() {
        decimalFormatter = DecimalFormatterImpl(rh)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.androidInjector()).thenReturn(injector.androidInjector())
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        Mockito.`when`(nsClientSource.isEnabled()).thenReturn(true)
        dataWorkerStorage = DataWorkerStorage(context)
        receiverDelegate = ReceiverDelegate(rxBus, rh, sp, receiverStatusStore, aapsSchedulers, fabricPrivacy)
        nsClientV3Plugin = NSClientV3Plugin(
            injector, aapsLogger, aapsSchedulers, rxBus, rh, context, fabricPrivacy,
            sp, receiverDelegate, config, dateUtil, uiInteraction, dataSyncSelectorV3, persistenceLayer,
            nsDeviceStatusHandler, nsClientSource, nsIncomingDataProcessor, storeDataForDb, decimalFormatter
        )
        nsClientV3Plugin.newestDataOnServer = LastModified(LastModified.Collections())
    }

    @Test
    fun notInitializedAndroidClient() = runTest {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()

        val result = sut.doWorkAndLog()
        assertIs<ListenableWorker.Result.Failure>(result)
    }

    @Test
    fun notEnabledNSClientSource() = runTest {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsClientSource.isEnabled()).thenReturn(false)
        Mockito.`when`(sp.getBoolean(app.aaps.core.utils.R.string.key_ns_receive_cgm, false)).thenReturn(false)

        val result = sut.doWorkAndLog()
        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(result.outputData.getString("Result")).isEqualTo("Load not enabled")
    }

    @Test
    fun testThereAreNewerDataFirstLoadEmptyReturn() = runTest {
        Mockito.`when`(workManager.beginUniqueWork(anyString(), any(), any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        Mockito.`when`(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.entries).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
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
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt())).thenReturn(NSAndroidClient.ReadResponse(200, 0, listOf(glucoseValue.toNSSvgV3())))

        val result = sut.doWorkAndLog()
        assertIs<ListenableWorker.Result.Success>(result)
    }

    @Test
    fun testNoLoadNeeded() = runTest {
        Mockito.`when`(workManager.beginUniqueWork(anyString(), any(), any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        Mockito.`when`(workContinuation.then(any<OneTimeWorkRequest>())).thenReturn(workContinuation)
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        nsClientV3Plugin.newestDataOnServer?.collections?.entries = now - 2000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyInt())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        assertThat(nsClientV3Plugin.lastLoadedSrvModified.collections.entries).isEqualTo(now - 1000)
        assertIs<ListenableWorker.Result.Success>(result)
    }
}
