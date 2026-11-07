package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [DataHandlerMobile.buildGraphData]: the watch gets only the BG history it can draw (issue #5241).
 *
 * The bucketed table holds 24 h plus the longest DIA; every watch surface draws at most 6 h. The whole
 * table went out on every resend, about one per BG, so the cut is what this locks in.
 */
class DataHandlerMobileGraphDataTest : TestBaseWithProfile() {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var trendCalculator: TrendCalculator
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var uiInteraction: UiInteraction
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var importExportPrefs: ImportExportPrefs
    @Mock private lateinit var pumpStatusProvider: PumpStatusProvider
    @Mock private lateinit var runningModeGuard: RunningModeGuard
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var wizardExecutor: WizardExecutor
    @Mock private lateinit var automation: Automation
    private lateinit var sut: DataHandlerMobile
    private val ads = mock<AutosensDataStore>()

    @BeforeEach fun prepare() {
        sut = DataHandlerMobile(
            context, rxBus, aapsLogger, rh, preferences, config,
            iobCobCalculator, processedTbrEbData, smbGlucoseStatusProvider, profileFunction, profileUtil,
            loop, processedDeviceStatusData, receiverStatusStore, quickWizard, trendCalculator, dateUtil,
            constraintsChecker, activePlugin, commandQueue, fabricPrivacy, uiInteraction,
            persistenceLayer, importExportPrefs, decimalFormatter, pumpStatusProvider,
            ch, runningModeGuard, wizardBolusExecutor, batchExecutor, wizardExecutor
        )
        sut.automation = automation
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    /** One bucket per 5 min from now back [hours] hours, newest first like the real table. */
    private fun bucketedTable(hours: Long): MutableList<InMemoryGlucoseValue> {
        val now = dateUtil.now()
        val step = T.mins(5).msecs()
        return (0 until T.hours(hours).msecs() / step).map { i ->
            InMemoryGlucoseValue(timestamp = now - i * step, value = 100.0 + i)
        }.toMutableList()
    }

    // The table is built BEFORE the stubbing line: bucketedTable() reads the dateUtil spy, and a spy
    // call inside whenever(...).thenReturn(...) is an "unfinished stubbing" to Mockito.
    @Test fun `graph data holds only the last 6 h of a 34 h table`() = runTest {
        val table = bucketedTable(hours = 34)
        whenever(ads.getBucketedDataTableCopy()).thenReturn(table)

        val graph = sut.buildGraphData()!!

        val oldest = dateUtil.now() - T.hours(6).msecs()
        assertThat(graph.entries).isNotEmpty()
        assertThat(graph.entries.all { it.timeStamp >= oldest }).isTrue()
        // 6 h of 5 min buckets, give or take the bucket on the boundary
        assertThat(graph.entries.size).isAtMost(6 * 12 + 1)
        assertThat(graph.entries.size).isAtLeast(6 * 12 - 1)
    }

    @Test fun `a table shorter than 6 h goes out whole`() = runTest {
        val table = bucketedTable(hours = 2)
        whenever(ads.getBucketedDataTableCopy()).thenReturn(table)

        val graph = sut.buildGraphData()!!

        assertThat(graph.entries.size).isEqualTo(2 * 12)
    }

    @Test fun `no bucketed table means no graph message`() = runTest {
        whenever(ads.getBucketedDataTableCopy()).thenReturn(null)

        assertThat(sut.buildGraphData()).isNull()
    }
}
