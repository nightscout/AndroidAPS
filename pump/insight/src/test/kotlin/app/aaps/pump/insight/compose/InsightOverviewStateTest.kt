package app.aaps.pump.insight.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.insight.R
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.descriptors.InsightState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR

/**
 * Unit test for [InsightOverviewState], the Insight pump overview state holder. Unlike other pump
 * overviews it is NOT a ViewModel and does not use viewModelScope — [InsightComposeContent] builds
 * it via `remember`. It exposes `uiState: StateFlow<PumpOverviewUiState>` whose value is computed at
 * construction by `buildUiState()`, which only reads [InsightPlugin.connectionService] and
 * `insightPlugin.isInitialized()`. The rx subscription lives in `start()`, which the tests never
 * call, so no rx / dispatcher wiring is needed. Rendering itself is covered by core:ui's
 * PumpOverviewScreenTest (the OVERVIEW screen just delegates to PumpOverviewScreen).
 */
internal class InsightOverviewStateTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var context: Context
    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var ch: ConcentrationHelper

    private val insightPlugin: InsightPlugin = mock()
    private val service: InsightConnectionService = mock()

    // appScope is only used by action onClick handlers, never at construction / in buildUiState().
    private val appScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    private fun createState() = InsightOverviewState(
        insightPlugin = insightPlugin,
        rh = rh,
        rxBus = rxBus,
        dateUtil = dateUtil,
        commandQueue = commandQueue,
        context = context,
        aapsSchedulers = aapsSchedulers,
        ch = ch,
        appScope = appScope
    )

    @Test
    fun noConnectionService_producesEmptyState() {
        whenever(insightPlugin.connectionService).thenReturn(null)
        whenever(insightPlugin.isInitialized()).thenReturn(false)

        val state = createState().uiState.value

        // service == null -> no info rows; !isInitialized -> no primary actions; null service -> no management actions
        assertThat(state.infoRows).isEmpty()
        assertThat(state.primaryActions).isEmpty()
        assertThat(state.managementActions).isEmpty()
        assertThat(state.statusBanner).isNull()
    }

    @Test
    fun pairedButNotInitialized_buildsStatusRow_andOffersUnpair() {
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(false)   // skips the whole isInitialized info block
        whenever(service.state).thenReturn(InsightState.DISCONNECTED)
        whenever(service.isPaired).thenReturn(true)
        // service.lastConnected defaults to 0L on the mock -> "last connected" row skipped.
        // pumpSystemIdentification / pumpFirmwareVersions / bluetoothAddress default to null -> identity rows skipped.

        whenever(rh.gs(R.string.insight_status)).thenReturn("Status")
        whenever(rh.gs(CoreUiR.string.disconnected)).thenReturn("Disconnected")
        whenever(rh.gs(R.string.unpair)).thenReturn("Unpair")

        val state = createState().uiState.value

        val rows = state.infoRows.filterIsInstance<PumpInfoRow>()
        assertThat(rows).isNotEmpty()
        val statusRow = rows.first { it.label == "Status" }
        assertThat(statusRow.value).isEqualTo("Disconnected")
        // !isInitialized -> no primary actions
        assertThat(state.primaryActions).isEmpty()
        // paired -> management offers Unpair, not Pairing
        assertThat(state.managementActions.map { it.label }).containsExactly("Unpair")
    }
}
