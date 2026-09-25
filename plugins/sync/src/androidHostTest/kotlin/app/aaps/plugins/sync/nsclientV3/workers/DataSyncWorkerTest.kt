package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.ListenableWorker.Result.Success
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.compose.NSClientRepositoryImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var context: Context

    private lateinit var nsClientMvvmRepository: NSClientRepositoryImpl

    private lateinit var sut: DataSyncWorker

    private fun buildSut(): DataSyncWorker =
        TestListenableWorkerBuilder<DataSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    DataSyncWorker(appContext, workerParameters, aapsLogger, fabricPrivacy, DataSyncRunner(aapsLogger, dataSyncSelectorV3, activePlugin, nsClientV3Plugin, nsClientMvvmRepository))
            })
            .build()

    @BeforeEach
    fun prepare() {
        nsClientMvvmRepository = NSClientRepositoryImpl(rxBus, aapsLogger)
        whenever(context.applicationContext).thenReturn(context)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))
        whenever(nsClientV3Plugin.doingFullSync).thenReturn(false)
    }

    @Test
    fun `doWorkAndLog does not upload when no write permission and not connected`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, never()).doUpload()
        verify(nsClientV3Plugin).scheduleIrregularExecution(refreshToken = true)
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog uploads when has write permission`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(true)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, times(1)).doUpload()
        verify(nsClientV3Plugin, never()).scheduleIrregularExecution(any())
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog uploads when websocket is connected`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(true))

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, times(1)).doUpload()
        verify(nsClientV3Plugin, never()).scheduleIrregularExecution(any())
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog uploads when both write permission and websocket connected`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(true)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(true))

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, times(1)).doUpload()
        verify(nsClientV3Plugin, never()).scheduleIrregularExecution(any())
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog ends full sync when doingFullSync is true`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.doingFullSync).thenReturn(true)
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(true)

        val result = sut.doWorkAndLog()

        verify(nsClientV3Plugin).endFullSync()
        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "● RUN" && it.logText == "Full sync finished" }).isTrue()
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog sends upload start and end events when uploading`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(true)

        sut.doWorkAndLog()

        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "► UPL" && it.logText == "Start" }).isTrue()
        assertThat(logs.any { it.action == "► UPL" && it.logText == "End" }).isTrue()
    }

    @Test
    fun `doWorkAndLog schedules token refresh when no write permission and not connected`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))

        sut.doWorkAndLog()

        verify(nsClientV3Plugin).scheduleIrregularExecution(refreshToken = true)
    }

    @Test
    fun `doWorkAndLog handles a connection that is not up`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))

        val result = sut.doWorkAndLog()

        verify(dataSyncSelectorV3, never()).doUpload()
        verify(nsClientV3Plugin).scheduleIrregularExecution(refreshToken = true)
        assertIs<Success>(result)
    }

    @Test
    fun `doWorkAndLog always returns success`() = runTest(timeout = 30.seconds) {
        sut = buildSut()

        // Test with various conditions
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(false)
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))
        assertIs<Success>(sut.doWorkAndLog())

        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(true)
        assertIs<Success>(sut.doWorkAndLog())

        whenever(nsClientV3Plugin.doingFullSync).thenReturn(true)
        assertIs<Success>(sut.doWorkAndLog())
    }

    @Test
    fun `doWorkAndLog does not end full sync when doingFullSync is false`() = runTest(timeout = 30.seconds) {
        sut = buildSut()
        whenever(nsClientV3Plugin.doingFullSync).thenReturn(false)
        whenever(nsClientV3Plugin.hasWritePermission).thenReturn(true)

        sut.doWorkAndLog()

        verify(nsClientV3Plugin, never()).endFullSync()
    }
}
