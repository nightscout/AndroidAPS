package app.aaps.pump.virtual

import android.content.Context
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.pump.virtual.keys.VirtualBooleanNonPreferenceKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

internal class VirtualPumpViewModelTest {

    @Mock private lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var context: Context

    // PumpCommunicationStatus subscribes to these in its init; they never emit so the status
    // banner / queue stay null and the launched collectors just park.
    private val pumpStatusFlow = MutableSharedFlow<EventPumpStatusChanged>()
    private val queueChangedFlow = MutableSharedFlow<EventQueueChanged>()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // rxBus flows consumed by PumpCommunicationStatus init
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(pumpStatusFlow)
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(queueChangedFlow)
        // DB change flows merged into dbChanged (evaluated eagerly in the constructor)
        whenever(persistenceLayer.observeChanges(TB::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EB::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        // plugin StateFlows used as combine() sources
        whenever(virtualPumpPlugin.pumpTypeFlow).thenReturn(MutableStateFlow<PumpType?>(null))
        whenever(virtualPumpPlugin.batteryPercentFlow).thenReturn(MutableStateFlow(50))
        whenever(virtualPumpPlugin.reservoirInUnitsFlow).thenReturn(MutableStateFlow(50))
        // suspend preference observed by combine() and read by buildInitialState()
        whenever(preferences.observe(VirtualBooleanNonPreferenceKey.IsSuspended)).thenReturn(MutableStateFlow(false))
        // management-action labels resolved in buildInitialState()
        whenever(rh.gs(R.string.pump_suspend)).thenReturn("Suspend")
        whenever(rh.gs(R.string.pump_resume)).thenReturn("Resume")
    }

    // Dispatchers.Unconfined runs the PumpCommunicationStatus init collectors eagerly; reading
    // uiState.value returns the WhileSubscribed seed (buildInitialState) without a collector, so
    // the combine/buildUiState block never runs and only the constructor-time stubs are touched.
    private fun viewModel() = VirtualPumpViewModel(
        virtualPumpPlugin = virtualPumpPlugin,
        rh = rh,
        pumpSync = pumpSync,
        dateUtil = dateUtil,
        persistenceLayer = persistenceLayer,
        preferences = preferences,
        ch = ch,
        rxBus = rxBus,
        commandQueue = commandQueue,
        context = context,
        scope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun uiState_default_hasSuspendAction_andEmptyInfoRows() {
        whenever(preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)).thenReturn(false)

        val sut = viewModel()
        val state = sut.uiState.value

        assertThat(state.infoRows).isEmpty()
        assertThat(state.primaryActions).isEmpty()
        assertThat(state.statusBanner).isNull()
        assertThat(state.queueStatus).isNull()
        // suspend (visible when running) + resume (visible when suspended)
        assertThat(state.managementActions).hasSize(2)
        assertThat(state.managementActions[0].label).isEqualTo("Suspend")
        assertThat(state.managementActions[0].visible).isTrue()
        assertThat(state.managementActions[1].label).isEqualTo("Resume")
        assertThat(state.managementActions[1].visible).isFalse()
    }

    @Test
    fun uiState_whenSuspended_showsResumeAction() {
        whenever(preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)).thenReturn(true)
        whenever(preferences.observe(VirtualBooleanNonPreferenceKey.IsSuspended)).thenReturn(MutableStateFlow(true))

        val sut = viewModel()
        val state = sut.uiState.value

        assertThat(state.managementActions).hasSize(2)
        // suspend hidden, resume shown
        assertThat(state.managementActions[0].visible).isFalse()
        assertThat(state.managementActions[1].visible).isTrue()
    }
}
