package app.aaps.implementation.maintenance

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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

    private fun apsData(input: String = """{"a":1}""", output: String? = """{"b":2}""") =
        ImportExportPrefsImpl.ApsResultExportWorker.ApsResultData("SMB", input, output)

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

    /**
     * The documents arrive as text and must end up **nested** in the envelope. Writing them with a
     * plain string put would quote and escape each one into a single string value - which compiles,
     * never throws, and silently produces a file the tooling can no longer read. `getJSONObject`
     * fails on a string value, so this test bites exactly there.
     */
    @Test
    fun `documents are nested rather than written as escaped text`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(true)
        whenever(dataInbox.drain(ApsExportInbox)).thenReturn(listOf(apsData()))
        val written = argumentCaptor<String>()

        worker().doWorkAndLog()

        verify(storage).putFileContents(any<File>(), written.capture())
        val json = JSONObject(written.firstValue)
        assertThat(json.getString("algorithm")).isEqualTo("SMB")
        assertThat(json.getJSONObject("input").getInt("a")).isEqualTo(1)
        assertThat(json.getJSONObject("output").getInt("b")).isEqualTo(2)
    }

    /** A run with no output left the key out before, and still does. */
    @Test
    fun `a missing output leaves the key out`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(true)
        whenever(dataInbox.drain(ApsExportInbox)).thenReturn(listOf(apsData(output = null)))
        val written = argumentCaptor<String>()

        worker().doWorkAndLog()

        verify(storage).putFileContents(any<File>(), written.capture())
        assertThat(JSONObject(written.firstValue).has("output")).isFalse()
    }

    /**
     * Parsing the text back is a failure mode that could not exist while the caller handed over
     * objects. One bad document must cost that one file, not the whole worker run.
     */
    @Test
    fun `unparsable input fails without throwing out of the worker`() = runTest {
        whenever(config.isEngineeringMode()).thenReturn(true)
        whenever(dataInbox.drain(ApsExportInbox)).thenReturn(listOf(apsData(input = "not json")))

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
        verify(storage, never()).putFileContents(any<File>(), any())
    }
}
