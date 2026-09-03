package app.aaps.ui.compose.maintenance

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ImportViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var importExportPrefs: ImportExportPrefs
    @Mock private lateinit var prefFileList: FileListProvider
    @Mock private lateinit var configBuilder: ConfigBuilder
    @Mock private lateinit var rh: TextResolver
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var overviewDataCache: OverviewDataCache
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var pump: PumpWithConcentration
    @Mock private lateinit var ads: AutosensDataStore

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var sut: ImportViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // viewModelScope launches (startImport / decrypt / confirmImport) are deferred by StandardTestDispatcher;
        // the pure synchronous setters below never enter viewModelScope, so no advance is needed.
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.serialNumber()).thenReturn("sn")
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(iobCobCalculator.ads).thenReturn(ads)
        sut = ImportViewModel(
            aapsLogger, importExportPrefs, prefFileList, configBuilder, rh, uel,
            commandQueue, pumpSync, activePlugin, overviewDataCache, iobCobCalculator
        )
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

    /**
     * Import used to end by killing the process, which guaranteed the new settings were in force.
     * Applying in place is the only ending iOS can have, so these pin what "applied" now means.
     */
    @Test
    fun `applying re-reads the plugin configuration`() = runTest(testDispatcher) {
        whenever(commandQueue.size()).thenReturn(0)
        whenever(commandQueue.performing()).thenReturn(null)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(configBuilder).initialize()
        assertThat(sut.importStep.value).isEqualTo(ImportStep.Idle)
    }

    /** The caches meant something under the old profile and targets; they cannot survive an import. */
    @Test
    fun `applying resets the calculation caches`() = runTest(testDispatcher) {
        whenever(commandQueue.size()).thenReturn(0)
        whenever(commandQueue.performing()).thenReturn(null)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(overviewDataCache).reset()
        verify(iobCobCalculator).clearCache()
        verify(ads).reset()
    }

    /**
     * The pump layer is asked from what is actually registered rather than from the imported
     * preferences. An unchanged pump must keep its running temporary basal, which `connectNewPump`
     * would end.
     */
    @Test
    fun `an unchanged pump is not reconnected`() = runTest(testDispatcher) {
        whenever(commandQueue.size()).thenReturn(0)
        whenever(commandQueue.performing()).thenReturn(null)
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(pumpSync, never()).connectNewPump()
    }

    @Test
    fun `a pump that no longer matches is reconnected`() = runTest(testDispatcher) {
        whenever(commandQueue.size()).thenReturn(0)
        whenever(commandQueue.performing()).thenReturn(null)
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(false)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(pumpSync).connectNewPump()
    }

    /**
     * The regression this whole ordering exists for: nothing may be applied while the pump is mid
     * command, because applying can stop and start the pump driver.
     */
    @Test
    fun `a busy pump is waited for before anything is applied`() = runTest(testDispatcher) {
        whenever(commandQueue.size()).thenReturn(1, 1, 0)
        whenever(commandQueue.performing()).thenReturn(null)

        sut.onApplyConfirmed()
        advanceTimeBy(100)

        assertThat(sut.importStep.value).isEqualTo(ImportStep.WaitingForPump)
        verify(configBuilder, never()).initialize()
    }
}
