package app.aaps.ui.compose.maintenance

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.CoreUiStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class MaintenanceViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var l: L
    @Mock private lateinit var maintenance: Maintenance
    @Mock private lateinit var importExportPrefs: ImportExportPrefs
    @Mock private lateinit var fileListProvider: FileListProvider
    @Mock private lateinit var cloudDirectoryManager: CloudDirectoryManager
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var dataSyncSelectorXdrip: DataSyncSelectorXdrip
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var overviewData: OverviewData
    @Mock private lateinit var overviewDataCache: OverviewDataCache
    @Mock private lateinit var nsClient: NsClient

    private lateinit var sut: MaintenanceViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched refreshExportConfig() coroutine
        // (no advanceUntilIdle), so construction stays clean and we test the synchronous state methods
        // against the default state.
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        sut = MaintenanceViewModel(
            aapsLogger, rh, l, maintenance, importExportPrefs, fileListProvider, cloudDirectoryManager,
            activePlugin, persistenceLayer, fabricPrivacy, uel, dataSyncSelectorXdrip, pumpSync,
            iobCobCalculator, overviewData, overviewDataCache, nsClient
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state is idle export, hidden cloud directory, no config`() {
        assertThat(sut.exportState.value).isEqualTo(MaintenanceViewModel.ExportState.Idle)
        assertThat(sut.cloudDirectoryState.value).isEqualTo(MaintenanceViewModel.CloudDirectoryState.Hidden)
        assertThat(sut.exportConfig.value).isNull()
        assertThat(sut.isDirectoryAccessGranted.value).isFalse()
    }

    @Test
    fun `onExportConfirmed moves export state to AskPassword`() {
        sut.onExportConfirmed()

        assertThat(sut.exportState.value).isEqualTo(MaintenanceViewModel.ExportState.AskPassword())
    }

    @Test
    fun `cancelExport resets export state to Idle`() {
        sut.onExportConfirmed()
        sut.cancelExport()

        assertThat(sut.exportState.value).isEqualTo(MaintenanceViewModel.ExportState.Idle)
    }

    @Test
    fun `onExportPasswordEntered rejects a password that is not the master password`() {
        whenever(importExportPrefs.isMasterPasswordCorrect("not-the-master")).thenReturn(false)
        sut.onExportConfirmed()

        sut.onExportPasswordEntered("not-the-master")

        // The wrong password is NOT cached and NOT used to export; the dialog stays open for a retry.
        verify(importExportPrefs, never()).cacheExportPassword(any())
        // The dialog reappears with the inline wrong-password error instead of silently staying open.
        assertThat(sut.exportState.value).isEqualTo(MaintenanceViewModel.ExportState.AskPassword(wrongPassword = true))
    }

    @Test
    fun `onExportPasswordEntered accepts and caches the master password`() {
        whenever(importExportPrefs.isMasterPasswordCorrect("master")).thenReturn(true)
        whenever(importExportPrefs.cacheExportPassword("master")).thenReturn("master")
        sut.onExportConfirmed()

        sut.onExportPasswordEntered("master")

        verify(importExportPrefs).cacheExportPassword("master")
        assertThat(sut.exportState.value).isEqualTo(MaintenanceViewModel.ExportState.Idle)
    }

    // The four below wait in real time rather than on the test scheduler. Every maintenance action
    // hops to `aapsIoDispatcher` for its slow part, and that is a real dispatcher on a real thread
    // pool - a global `expect val`, so a test cannot swap it. Virtual time simply does not reach it:
    // `advanceUntilIdle` returns while the work is still on its way, and the assertion then reads an
    // empty list and calls a working feature broken. `Main` is moved to an unconfined dispatcher for
    // the same reason, so the coroutine finishes on whichever thread resumed it.

    @Test
    fun `sendLogs says so on screen when the platform has no way to send them`() = runBlocking {
        // What iOS does: there is no mail composer, so Maintenance throws instead of pretending.
        // NotImplementedError is an Error rather than an Exception, so before the shared handler it
        // walked straight past `catch (e: Exception)` and took the app down.
        runEagerly()
        whenever(rh.gs(CoreUiStrings.not_implemented_yet)).thenReturn("not ready here")
        whenever(maintenance.executeSendLogs()).thenAnswer { throw NotImplementedError("no mail composer") }
        val event = expectEvent()

        sut.sendLogs()

        assertThat(event.await()).isEqualTo(MaintenanceEvent.Error("not ready here"))
    }

    @Test
    fun `resetDatabases says so on screen when the platform cannot clear them`() = runBlocking {
        // Desktop still answers this way, and iOS did until the tables were cleared with SQL.
        runEagerly()
        whenever(rh.gs(CoreUiStrings.not_implemented_yet)).thenReturn("not ready here")
        whenever(persistenceLayer.clearDatabases()).thenAnswer { throw UnsupportedOperationException("no clearAllTables") }
        val event = expectEvent()

        sut.resetDatabases()

        assertThat(event.await()).isEqualTo(MaintenanceEvent.Error("not ready here"))
    }

    @Test
    fun `a real failure gets the plain error message, not the not-ready one`() = runBlocking {
        runEagerly()
        whenever(rh.gs(CoreUiStrings.error)).thenReturn("error")
        whenever(persistenceLayer.cleanupDatabase(any(), any())).thenAnswer { throw IllegalStateException("database is locked") }
        val event = expectEvent()

        sut.cleanupDatabases()

        assertThat(event.await()).isEqualTo(MaintenanceEvent.Error("error"))
    }

    @Test
    fun `cleanupDatabases reports what it removed when it works`() = runBlocking {
        runEagerly()
        whenever(persistenceLayer.cleanupDatabase(any(), any())).thenReturn("GlucoseValue 12")
        val event = expectEvent()

        sut.cleanupDatabases()

        assertThat(event.await()).isEqualTo(MaintenanceEvent.CleanupResult("GlucoseValue 12"))
    }

    /**
     * Lets the view model's coroutines run as they are started.
     *
     * Called after the view model is built, never before: the standard dispatcher from `setUp` is
     * what keeps the `init` block's `refreshExportConfig` from running against mocks nothing has
     * stubbed, and that would fail every test in the class rather than only these.
     */
    private fun runEagerly() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /**
     * Starts listening and returns before the action does anything.
     *
     * `events` has no replay and no buffer, so an event sent before anybody listens is not kept and
     * not delivered - waiting for the subscription first is what makes the test about the action
     * rather than about who won the race. The timeout is only so a broken action fails as a test
     * instead of hanging the build.
     */
    private suspend fun CoroutineScope.expectEvent(): Deferred<MaintenanceEvent> {
        val listening = CompletableDeferred<Unit>()
        val event = async(Dispatchers.Default) {
            withTimeout(10_000) { sut.events.onSubscription { listening.complete(Unit) }.first() }
        }
        listening.await()
        return event
    }
}
