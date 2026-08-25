package app.aaps.pump.common.compose

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit test for [RileyLinkStatusViewModel].
 *
 * StandardTestDispatcher DEFERS the `init{}`-launched collector coroutine (no advanceUntilIdle),
 * so construction only runs the synchronous `refresh()` call. When the active pump is NOT a
 * RileyLinkPumpDevice, `buildState()` short-circuits and returns the DEFAULT [RileyLinkStatusUiState],
 * which is what we assert deterministically here (no reliance on the launched flow collection).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RileyLinkStatusViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus

    // Concrete final classes -> mockito-kotlin mock() (Mockito 5 inline handles finals)
    private val rileyLinkServiceData: RileyLinkServiceData = mock()
    private val rileyLinkUtil: RileyLinkUtil = mock()

    // A plain Pump mock is NOT a RileyLinkPumpDevice, so the `as?` cast in buildState() yields null.
    private val activePump: Pump = mock()

    private lateinit var sut: RileyLinkStatusViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        // buildState() reads activePumpInternal first; a non-RileyLink pump makes it return default state.
        whenever(activePlugin.activePumpInternal).thenReturn(activePump)
        // Defensive: the deferred collector body calls rxBus.toFlow(...) before .collect { } if ever run.
        whenever(rxBus.toFlow(EventRileyLinkDeviceStatusChange::class.java)).thenReturn(emptyFlow())

        sut = RileyLinkStatusViewModel(
            rh, rileyLinkServiceData, rileyLinkUtil, activePlugin, dateUtil, preferences, rxBus
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state when active pump is not a RileyLink device`() {
        val state = sut.uiState.value

        assertThat(state).isEqualTo(RileyLinkStatusUiState())
        assertThat(state.address).isEqualTo("-")
        assertThat(state.name).isEqualTo("-")
        assertThat(state.batteryLevel).isNull()
        assertThat(state.connectionStatus).isEqualTo("-")
        assertThat(state.historyItems).isEmpty()
    }

    @Test
    fun `refresh keeps default state when no RileyLink pump`() {
        sut.refresh()

        assertThat(sut.uiState.value).isEqualTo(RileyLinkStatusUiState())
    }
}
