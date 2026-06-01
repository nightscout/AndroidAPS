package app.aaps.implementation.maintenance

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.keys.BooleanNonKey
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class CsvExportWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var prefFileList: FileListProvider
    @Mock lateinit var userEntryPresentationHelper: UserEntryPresentationHelper
    @Mock lateinit var storage: Storage
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var cloudStorageManager: CloudStorageManager
    @Mock lateinit var workerParameters: WorkerParameters

    private fun worker() =
        ImportExportPrefsImpl.CsvExportWorker(
            context, workerParameters, aapsLogger, fabricPrivacy, rh, prefFileList, userEntryPresentationHelper,
            storage, persistenceLayer, cloudStorageManager, preferences, rxBus
        )

    @BeforeEach
    fun stubResources() {
        whenever(rh.gs(any<Int>())).thenReturn("msg")
    }

    private suspend fun localOnly() {
        whenever(preferences.get(BooleanNonKey.ExportCsvLocalEnabled)).thenReturn(true)
        whenever(preferences.get(BooleanNonKey.ExportCsvCloudEnabled)).thenReturn(false)
        whenever(persistenceLayer.getUserEntryFilteredDataFromTime(any())).thenReturn(emptyList())
        whenever(userEntryPresentationHelper.userEntriesToCsv(any())).thenReturn("csv")
    }

    @Test
    fun `local export writes file and returns success`() = runTest {
        localOnly()
        whenever(cloudStorageManager.isCloudStorageActive()).thenReturn(false)
        whenever(prefFileList.newExportCsvFile()).thenReturn(mock<DocumentFile>())
        whenever(context.contentResolver).thenReturn(mock<ContentResolver>())

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(storage).putFileContents(anyOrNull(), any<DocumentFile>(), any())
    }

    @Test
    fun `local export returns failure when no file available`() = runTest {
        localOnly()
        whenever(cloudStorageManager.isCloudStorageActive()).thenReturn(false)
        whenever(prefFileList.newExportCsvFile()).thenReturn(null)

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
    }

    @Test
    fun `cloud export returns failure when no active provider`() = runTest {
        whenever(preferences.get(BooleanNonKey.ExportCsvLocalEnabled)).thenReturn(false)
        whenever(preferences.get(BooleanNonKey.ExportCsvCloudEnabled)).thenReturn(true)
        whenever(cloudStorageManager.isCloudStorageActive()).thenReturn(true)
        whenever(persistenceLayer.getUserEntryFilteredDataFromTime(any())).thenReturn(emptyList())
        whenever(cloudStorageManager.getActiveProvider()).thenReturn(null)

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
    }
}
