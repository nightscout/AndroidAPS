package app.aaps.ui.compose.maintenance

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ImportViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var importExportPrefs: ImportExportPrefs
    @Mock private lateinit var prefFileList: FileListProvider
    @Mock private lateinit var configBuilder: ConfigBuilder

    private lateinit var sut: ImportViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // viewModelScope launches (startImport / decrypt / confirmImport) are deferred by StandardTestDispatcher;
        // the pure synchronous setters below never enter viewModelScope, so no advance is needed.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = ImportViewModel(aapsLogger, importExportPrefs, prefFileList, configBuilder)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default step is Idle`() {
        assertThat(sut.importStep.value).isEqualTo(ImportStep.Idle)
    }

    @Test
    fun `cancelImport resets to Idle`() {
        sut.cancelImport()
        assertThat(sut.importStep.value).isEqualTo(ImportStep.Idle)
    }

    @Test
    fun `dismissError resets to Idle`() {
        sut.dismissError()
        assertThat(sut.importStep.value).isEqualTo(ImportStep.Idle)
    }

    @Test
    fun `goBackToFilePicker shows empty FilePicker with default LOCAL source`() {
        sut.goBackToFilePicker()
        val step = sut.importStep.value
        assertThat(step).isInstanceOf(ImportStep.FilePicker::class.java)
        val picker = step as ImportStep.FilePicker
        assertThat(picker.files).isEmpty()
        assertThat(picker.hasMoreCloud).isFalse()
        assertThat(picker.isLoadingMore).isFalse()
        assertThat(picker.source).isEqualTo(ImportSource.LOCAL)
    }

    @Test
    fun `selectFile moves to Review carrying file and needsDecryptionPassword when no master password`() {
        val prefsFile = PrefsFile("backup.json", "", emptyMap())
        whenever(importExportPrefs.isMasterPasswordSet()).thenReturn(false)

        sut.selectFile(ImportFileItem(prefsFile, ImportSource.LOCAL))

        val step = sut.importStep.value
        assertThat(step).isInstanceOf(ImportStep.Review::class.java)
        val review = step as ImportStep.Review
        assertThat(review.file).isEqualTo(prefsFile)
        assertThat(review.fileSource).isEqualTo(ImportSource.LOCAL)
        assertThat(review.needsDecryptionPassword).isTrue()
    }

    @Test
    fun `selectFile does not request decryption password when master password is set`() {
        val prefsFile = PrefsFile("backup.json", "", emptyMap())
        whenever(importExportPrefs.isMasterPasswordSet()).thenReturn(true)

        sut.selectFile(ImportFileItem(prefsFile, ImportSource.CLOUD))

        val review = sut.importStep.value as ImportStep.Review
        assertThat(review.fileSource).isEqualTo(ImportSource.CLOUD)
        assertThat(review.needsDecryptionPassword).isFalse()
    }

    @Test
    fun `onMasterPasswordChanged is a no-op while step is Idle`() {
        sut.onMasterPasswordChanged("secret")
        assertThat(sut.importStep.value).isEqualTo(ImportStep.Idle)
    }
}
