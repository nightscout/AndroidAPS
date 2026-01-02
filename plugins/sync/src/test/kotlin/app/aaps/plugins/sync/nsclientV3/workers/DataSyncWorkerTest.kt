package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ListenableWorker.Result.Success
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
internal class DataSyncWorkerTest : TestBase() {

    abstract class ContextWithInjector : Context(), HasAndroidInjector

    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var nsClient: NsClient
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var nsClientV3Service: NSClientV3Service
    @Mock lateinit var context: ContextWithInjector

    private lateinit var sut: DataSyncWorker

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is DataSyncWorker) {
                it.aapsLogger = aapsLogger
                it.fabricPrivacy = fabricPrivacy
                it.dataSyncSelectorV3 = dataSyncSelectorV3
                it.activePlugin = activePlugin
                it.rxBus = rxBus
                it.nsClientV3Plugin = nsClientV3Plugin
            }
        }
    }

    @BeforeEach
    fun prepare() {
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.androidInjector()).thenReturn(injector.androidInjector())
        whenever(activePlugin.activeNsClient).thenReturn(nsClient)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(null)
        whenever(nsClientV3Plugin.doingFullSync).thenReturn(false)
    }

    @Test
    fun `doWorkAndLog does not upload when no write permission and not connected`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(nsClientV3Service)
        whenever(nsClientV3Service.wsConnected).thenReturn(false)

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, never()).doUpload()
        verify(nsClientV3Plugin).scheduleIrregularExecution(refreshToken = true)
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog uploads when has write permission`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(true)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(nsClientV3Service)
        whenever(nsClientV3Service.wsConnected).thenReturn(false)

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, times(1)).doUpload()
        verify(nsClientV3Plugin, never()).scheduleIrregularExecution(any())
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog uploads when websocket is connected`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(nsClientV3Service)
        whenever(nsClientV3Service.wsConnected).thenReturn(true)

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, times(1)).doUpload()
        verify(nsClientV3Plugin, never()).scheduleIrregularExecution(any())
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog uploads when both write permission and websocket connected`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(true)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(nsClientV3Service)
        whenever(nsClientV3Service.wsConnected).thenReturn(true)

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, times(1)).doUpload()
        verify(nsClientV3Plugin, never()).scheduleIrregularExecution(any())
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog ends full sync when doingFullSync is true`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClientV3Plugin.doingFullSync).thenReturn(true)
        whenever(nsClient.hasWritePermission).thenReturn(true)

        val events = mutableListOf<EventNSClientNewLog>()
        val subscription = rxBus.toObservable(EventNSClientNewLog::class.java).subscribe { events.add(it) }

        val result = sut.doWorkAndLog()

        verify(nsClientV3Plugin).endFullSync()
        assertThat(events.any { it.action == "● RUN" && it.logText == "Full sync finished" }).isTrue()
        assertIs<Success>(result)
        subscription.dispose()
    }

    @Test
    fun `doWorkAndLog sends upload start and end events when uploading`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(true)

        val events = mutableListOf<EventNSClientNewLog>()
        val subscription = rxBus.toObservable(EventNSClientNewLog::class.java).subscribe { events.add(it) }

        sut.doWorkAndLog()

        assertThat(events.any { it.action == "► UPL" && it.logText == "Start" }).isTrue()
        assertThat(events.any { it.action == "► UPL" && it.logText == "End" }).isTrue()
        subscription.dispose()
    }

    @Test
    fun `doWorkAndLog schedules token refresh when no write permission and not connected`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(nsClientV3Service)
        whenever(nsClientV3Service.wsConnected).thenReturn(false)

        sut.doWorkAndLog()

        verify(nsClientV3Plugin).scheduleIrregularExecution(refreshToken = true)
    }

    @Test
    fun `doWorkAndLog handles null nsClientV3Service`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClient.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(null)

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, never()).doUpload()
        verify(nsClientV3Plugin).scheduleIrregularExecution(refreshToken = true)
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog always returns success`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()

        // Test with various conditions
        whenever(nsClient.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.nsClientV3Service).thenReturn(null)
        assertIs<Success>(sut.doWorkAndLog())

        whenever(nsClient.hasWritePermission).thenReturn(true)
        assertIs<Success>(sut.doWorkAndLog())

        whenever(nsClientV3Plugin.doingFullSync).thenReturn(true)
        assertIs<Success>(sut.doWorkAndLog())
    }

    @Test
    fun `doWorkAndLog does not end full sync when doingFullSync is false`() = runTest(timeout = 30.seconds) {
        sut = TestListenableWorkerBuilder<DataSyncWorker>(context).build()
        whenever(nsClientV3Plugin.doingFullSync).thenReturn(false)
        whenever(nsClient.hasWritePermission).thenReturn(true)

        sut.doWorkAndLog()

        verify(nsClientV3Plugin, never()).endFullSync()
    }
}
