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

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched refreshExportConfig() coroutine
        // (no advanceUntilIdle), so construction stays clean and we test the synchronous state methods
        // against the default state.
        Dispatchers.setMain(StandardTestDispatcher())
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
}
