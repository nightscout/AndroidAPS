package info.nightscout.plugins.sync.nsclientV3.workers

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.sync.nsclient.NsClientReceiverDelegate
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoadBgWorkerTest : TestBase() {

    @Mock lateinit var dataWorkerStorage: DataWorkerStorage
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

    private lateinit var nsClientV3Plugin: NSClientV3Plugin
    private lateinit var nsClientReceiverDelegate: NsClientReceiverDelegate
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
            }
        }
    }

    @BeforeEach
    fun setUp() {
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.androidInjector()).thenReturn(injector.androidInjector())
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        nsClientReceiverDelegate = NsClientReceiverDelegate(rxBus, rh, sp, receiverStatusStore)
        nsClientV3Plugin = NSClientV3Plugin(
            injector, aapsLogger, aapsSchedulers, rxBus, rh, context, fabricPrivacy, sp, nsClientReceiverDelegate, config, dateUtil, uiInteraction, dataSyncSelector,
            profileFunction, repository
        )

    }

    @Test
    fun notInitializedAndroidClient() = runTest {
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()

        val result = sut.doWorkAndLog()
        Assertions.assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun testThereAreNewerDataFirstLoadEmptyReturn() = runTest {
        initWorkManager()
        nsClientV3Plugin.nsAndroidClient = nsAndroidClient
        nsClientV3Plugin.lastLoadedSrvModified.collections.entries = 0L // first load
        nsClientV3Plugin.firstLoadContinueTimestamp.collections.entries = now - 1000
        sut = TestListenableWorkerBuilder<LoadBgWorker>(context).build()
        Mockito.`when`(nsAndroidClient.getSgvsNewerThan(anyLong(), anyLong())).thenReturn(NSAndroidClient.ReadResponse(200, 0, emptyList()))

        val result = sut.doWorkAndLog()
        Assertions.assertEquals(now - 1000, nsClientV3Plugin.lastLoadedSrvModified.collections.entries)
        Assertions.assertTrue(result is ListenableWorker.Result.Success)
    }
}