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
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.ui.UiRestartImpl
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
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.ui.CoreUiStrings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
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
    private val uiRestart = UiRestartImpl()

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
        // The steps carry resolved text, and an unstubbed mock hands back null into a non-null
        // parameter. Tests that care about the wording stub their own ref over the top of this.
        whenever(rh.gs(any<TextRef>())).thenReturn("message")
        sut = ImportViewModel(
            aapsLogger, importExportPrefs, prefFileList, configBuilder, rh, uel,
            commandQueue, pumpSync, activePlugin, overviewDataCache, iobCobCalculator, uiRestart
        )
        // Production hands the apply to the IO dispatcher, which a test cannot advance or observe.
        // Unconfined runs it inline instead, so `advanceUntilIdle` really does mean "the apply is done".
        sut.applyDispatcher = Dispatchers.Unconfined
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

    // ---- Applying the imported settings ----

    /**
     * Stands in for the real queue's `withHold`: runs the block when the hold is granted, skips it
     * when it is not. The production version is what actually stops a command starting mid-apply;
     * here it only has to reproduce the two outcomes the view model branches on.
     */
    private suspend fun queueGrantsHold(granted: Boolean) {
        whenever(commandQueue.withHold(any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<suspend () -> Unit>(2)
            if (granted) runBlocking { block() }
            granted
        }
    }

    /**
     * Import used to end by killing the process, which guaranteed the new settings were in force.
     * Applying in place is the only ending iOS can have, so these pin what "applied" now means.
     */
    @Test
    fun `applying re-reads the plugin configuration`() = runTest(testDispatcher) {
        queueGrantsHold(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        // applyConfiguration, not initialize: this one returns only once the plugins it starts and
        // stops have finished, which is the point of having waited for an idle pump.
        verify(configBuilder).applyConfiguration()
        verify(configBuilder, never()).initialize()
    }

    /**
     * The success ending has to be its own step. It used to fall back to Idle, which the screen draws
     * as a spinner that nothing ever takes down - so a finished import looked like a hung one.
     */
    @Test
    fun `applying ends on the applied step, not back on the spinner`() = runTest(testDispatcher) {
        queueGrantsHold(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        assertThat(sut.importStep.value).isEqualTo(ImportStep.Applied)
    }

    /** One audit entry per import, written when it is applied - not when the picker is opened. */
    @Test
    fun `applying writes one audit entry`() = runTest(testDispatcher) {
        queueGrantsHold(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(uel).log(Action.IMPORT_SETTINGS, Sources.Maintenance)
    }

    /** The caches meant something under the old profile and targets; they cannot survive an import. */
    @Test
    fun `applying resets the calculation caches`() = runTest(testDispatcher) {
        queueGrantsHold(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(overviewDataCache).reset()
        verify(iobCobCalculator).clearCache()
        verify(ads).reset()
    }

    /**
     * Device-found: emptying the overview cache is only half the job.
     *
     * `reset()` nulls the chip flows, and the database-backed ones stay null until something happens
     * to refresh each of them - for the profile that is the next ProfileSwitch, possibly hours off.
     * The overview showed "NO PROFILE SET" in large letters while the loop was running on that very
     * profile without trouble. The restart used to rebuild all of this on the way back up.
     */
    @Test
    fun `applying reloads the overview cache it just emptied`() = runTest(testDispatcher) {
        queueGrantsHold(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(overviewDataCache).refreshProfile()
        verify(overviewDataCache).refreshTempTarget()
        verify(overviewDataCache).refreshRunningMode()
        verify(overviewDataCache).refreshTbr()
    }

    /** ...and in that order: emptied first, then filled, never the other way round. */
    @Test
    fun `the overview cache is reloaded after it is reset`() = runTest(testDispatcher) {
        queueGrantsHold(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        inOrder(overviewDataCache) {
            verify(overviewDataCache).reset()
            verify(overviewDataCache).refreshProfile()
        }
    }

    /**
     * The pump layer is asked from what is actually registered rather than from the imported
     * preferences. An unchanged pump must keep its running temporary basal, which `connectNewPump`
     * would end.
     */
    @Test
    fun `an unchanged pump is not reconnected`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(pumpSync, never()).connectNewPump()
    }

    /** ...and its queued commands are still for it, so they must survive the apply. */
    @Test
    fun `an unchanged pump keeps its queued commands`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(true)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(commandQueue, never()).completeAllAsNoOp(any())
    }

    @Test
    fun `a pump that no longer matches is reconnected`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(false)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(pumpSync).connectNewPump()
    }

    /**
     * The worst outcome available here: a command queued for the pump that was active a moment ago
     * running against the different one the import selected. They are completed as a no-op instead,
     * so whoever is waiting on one is told it did not happen.
     */
    @Test
    fun `commands queued for the old pump are cancelled when the pump changes`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(false)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(commandQueue).completeAllAsNoOp(any())
    }

    /**
     * The regression this whole ordering exists for: nothing may be applied while the pump is mid
     * command, because applying can stop and start the pump driver. The wait itself now lives in
     * `withHold` - what matters here is that a refused hold applies nothing at all.
     */
    @Test
    fun `a pump that stays busy applies nothing`() = runTest(testDispatcher) {
        queueGrantsHold(false)

        sut.onApplyConfirmed()
        advanceUntilIdle()

        verify(configBuilder, never()).applyConfiguration()
        verify(pumpSync, never()).connectNewPump()
        verify(overviewDataCache, never()).reset()
    }

    /** ...and says so in a way the user can act on, rather than a dead end. */
    @Test
    fun `a pump that stays busy ends on a retryable step`() = runTest(testDispatcher) {
        queueGrantsHold(false)
        whenever(rh.gs(CoreUiStrings.import_apply_pump_busy)).thenReturn("busy")

        sut.onApplyConfirmed()
        advanceUntilIdle()

        assertThat(sut.importStep.value).isEqualTo(ImportStep.ApplyFailed("busy"))
    }

    /**
     * The settings are already written by this point, so a failed apply must not be the end of it.
     * Before, the busy message closed the screen and left the app running the old configuration with
     * no way back to it short of restarting the process - which iOS may never do.
     */
    @Test
    fun `retrying after a busy pump applies the settings`() = runTest(testDispatcher) {
        queueGrantsHold(false)
        sut.onApplyConfirmed()
        advanceUntilIdle()

        queueGrantsHold(true)
        sut.retryApply()
        advanceUntilIdle()

        verify(configBuilder).applyConfiguration()
        assertThat(sut.importStep.value).isEqualTo(ImportStep.Applied)
    }

    /**
     * A plugin that throws on the way up or down must not take the app with it. The settings are
     * already written by this point, so a crash here would bring the app back with them half applied
     * and no screen left to finish the job.
     */
    @Test
    fun `a plugin that throws while applying ends on a retryable step`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        whenever(configBuilder.applyConfiguration()).thenThrow(IllegalStateException("plugin blew up"))

        sut.onApplyConfirmed()
        advanceUntilIdle()

        assertThat(sut.importStep.value).isEqualTo(ImportStep.ApplyFailed("plugin blew up"))
    }

    /** Only after the user has seen the outcome does the screen go back to Idle and close. */
    @Test
    fun `finishing the apply returns to Idle`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        sut.onApplyConfirmed()
        advanceUntilIdle()

        sut.finishApply()

        assertThat(sut.importStep.value).isEqualTo(ImportStep.Idle)
    }

    /**
     * The app is running the imported settings, but a screen composed before that still shows what
     * it read - which looks exactly like an import that did nothing. Every shell rebuilds on this.
     *
     * Asked for when the user leaves the confirmation, not during the apply: Android answers a
     * rebuild by recreating the activity, which would take the "settings applied" dialog down before
     * it could be read.
     */
    @Test
    fun `finishing a successful apply asks the ui to rebuild`() = runTest(testDispatcher) {
        queueGrantsHold(true)
        val before = uiRestart.signal.value

        sut.onApplyConfirmed()
        advanceUntilIdle()
        assertThat(uiRestart.signal.value).isEqualTo(before)   // confirmation still on screen

        sut.finishApply()

        assertThat(uiRestart.signal.value).isGreaterThan(before)
    }

    /**
     * A pump that never goes idle: the settings are not applied, so there is nothing to rebuild for,
     * and the user is told rather than left in a dialog for ever.
     */
    @Test
    fun `a pump that never goes idle gives up instead of applying`() = runTest(testDispatcher) {
        queueGrantsHold(false)
        val before = uiRestart.signal.value

        sut.onApplyConfirmed()
        advanceUntilIdle()
        sut.finishApply()

        verify(configBuilder, never()).applyConfiguration()
        assertThat(uiRestart.signal.value).isEqualTo(before)
        assertThat(sut.importStep.value).isInstanceOf(ImportStep.Idle::class.java)
    }
}
