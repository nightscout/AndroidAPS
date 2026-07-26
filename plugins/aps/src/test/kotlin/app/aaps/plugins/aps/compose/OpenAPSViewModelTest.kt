package app.aaps.plugins.aps.compose

import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

internal class OpenAPSViewModelTest {

    @Mock private lateinit var apsPlugin: APS
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil

    private val updateGuiFlow = MutableSharedFlow<EventOpenAPSUpdateGui>()
    private val resetGuiFlow = MutableSharedFlow<EventResetOpenAPSGui>()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(rxBus.toFlow(EventOpenAPSUpdateGui::class.java)).thenReturn(updateGuiFlow)
        whenever(rxBus.toFlow(EventResetOpenAPSGui::class.java)).thenReturn(resetGuiFlow)
    }

    // Dispatchers.Unconfined runs the VM's launched coroutines (init updateState + onRefresh's
    // apsPlugin.invoke) eagerly and synchronously, so state and interactions settle in-line. The
    // never-completing toFlow subscriptions just park; a standalone scope has no leaked-coroutine check.
    private fun viewModel() =
        OpenAPSViewModel(apsPlugin, rxBus, rh, dateUtil, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun uiState_showsNotAvailable_whenNoApsResult() {
        whenever(apsPlugin.lastAPSResult).thenReturn(null)
        whenever(rh.gs(R.string.not_available_full)).thenReturn("N/A")

        val sut = viewModel()

        assertThat(sut.uiState.value.statusMessage).isEqualTo("N/A")
        assertThat(sut.uiState.value.sections).isEmpty()
    }

    @Test
    fun onRefresh_setsRefreshingState() {
        whenever(apsPlugin.lastAPSResult).thenReturn(null)

        val sut = viewModel()
        sut.onRefresh()

        // isRefreshing is flipped synchronously; the subsequent apsPlugin.invoke(...) is a
        // fire-and-forget launch, not asserted here to keep the test deterministic.
        assertThat(sut.uiState.value.isRefreshing).isTrue()
    }
}
