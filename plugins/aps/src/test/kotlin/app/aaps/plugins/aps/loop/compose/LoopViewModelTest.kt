package app.aaps.plugins.aps.loop.compose

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

internal class LoopViewModelTest {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences

    private val updateGuiFlow = MutableSharedFlow<EventLoopUpdateGui>()
    private val lastRunGuiFlow = MutableSharedFlow<EventLoopSetLastRunGui>()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(rxBus.toFlow(EventLoopUpdateGui::class.java)).thenReturn(updateGuiFlow)
        whenever(rxBus.toFlow(EventLoopSetLastRunGui::class.java)).thenReturn(lastRunGuiFlow)
    }

    // Dispatchers.Unconfined runs the VM's launched coroutines (init's updateState) eagerly and
    // synchronously, so the default state settles in-line. The never-completing toFlow
    // subscriptions just park; a standalone scope has no leaked-coroutine check.
    private fun viewModel() =
        LoopViewModel(loop, rxBus, rh, dateUtil, decimalFormatter, aapsLogger, preferences, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun uiState_showsNotAvailable_whenNoLastRun() {
        whenever(loop.lastRun).thenReturn(null)
        whenever(rh.gs(R.string.not_available_full)).thenReturn("N/A")

        val sut = viewModel()

        assertThat(sut.uiState.value.statusMessage).isEqualTo("N/A")
        assertThat(sut.uiState.value.lastRun).isEmpty()
    }

    @Test
    fun onRefresh_setsRefreshingState() {
        whenever(loop.lastRun).thenReturn(null)
        whenever(rh.gs(R.string.not_available_full)).thenReturn("N/A")

        val sut = viewModel()
        sut.onRefresh()

        // isRefreshing is flipped synchronously; the subsequent loop.invoke(...) is a
        // fire-and-forget launch, not asserted here to keep the test deterministic.
        assertThat(sut.uiState.value.isRefreshing).isTrue()
    }
}
