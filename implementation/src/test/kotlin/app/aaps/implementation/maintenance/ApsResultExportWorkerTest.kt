package app.aaps.implementation.maintenance

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import kotlin.test.assertIs

class ApsResultExportWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var prefFileList: FileListProvider
    @Mock lateinit var storage: Storage
    @Mock lateinit var dataInbox: DataInbox
    @Mock lateinit var workerParameters: WorkerParameters

    private fun worker() =
        ImportExportPrefsImpl.ApsResultExportWorker(context, workerParameters, aapsLogger, fabricPrivacy, prefFileList, storage, config, dataInbox)

    private fun apsData() =
        ImportExportPrefsImpl.ApsResultExportWorker.ApsResultData("SMB", JSONObject().put("a", 1), JSONObject().put("b", 2))

    @BeforeEach
    fun setup() {
        whenever(prefFileList.newResultFile()).thenReturn(File("result.json"))
    }

    @Test
    fun `not engineering mode skips export`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(false)

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(dataInbox, never()).drain(ApsExportInbox)
    }

    @Test
    fun `empty inbox returns no data`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(true)
        whenever(dataInbox.drain(ApsExportInbox)).thenReturn(emptyList())

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Success>(result)
        verify(storage, never()).putFileContents(any<File>(), any())
    }

    @Test
    fun `writes result file for each item`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(true)
        whenever(dataInbox.drain(ApsExportInbox)).thenReturn(listOf(apsData()))

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(prefFileList).ensureResultDirExists()
        verify(storage).putFileContents(any<File>(), any())
    }

    @Test
    fun `write failure returns failure`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(true)
        whenever(dataInbox.drain(ApsExportInbox)).thenReturn(listOf(apsData()))
        doThrow(IOException("disk full")).whenever(storage).putFileContents(any<File>(), any())

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
    }
}
