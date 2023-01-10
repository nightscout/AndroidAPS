package info.nightscout.plugins.sync.nsclientV3.workers

import androidx.work.ListenableWorker.Result.Success
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.rx.bus.RxBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
internal class DataSyncWorkerTest : TestBase() {

    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dataSyncSelector: DataSyncSelector
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var nsClient: NsClient
    @Mock lateinit var rxBus: RxBus

    private lateinit var sut: DataSyncWorker

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is DataSyncWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.dataSyncSelector = dataSyncSelector
                it.activePlugin = activePlugin
                it.rxBus = rxBus
            }
        }
    }

    @BeforeEach
    fun prepare() {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.androidInjector()).thenReturn(injector.androidInjector())
        `when`(activePlugin.activeNsClient).thenReturn(nsClient)
    }

    @Test
    fun doWorkAndLog() = runTest {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        `when`(nsClient.hasWritePermission).thenReturn(false)
        sut.doWorkAndLog()
        Mockito.verify(dataSyncSelector, Mockito.times(0)).doUpload()

        `when`(nsClient.hasWritePermission).thenReturn(true)
        val result = sut.doWorkAndLog()
        Mockito.verify(dataSyncSelector, Mockito.times(1)).doUpload()
        Assertions.assertTrue(result is Success)
    }
}