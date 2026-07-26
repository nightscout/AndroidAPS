package app.aaps.ui.compose.loopSheet

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class LoopActionViewModelTest {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: LoopActionViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // The init observer launchIns + the refreshState() coroutine are deferred by StandardTestDispatcher;
        // the observed rxBus flows are still built synchronously in init, so they must be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(rxBus.toFlow(EventLoopUpdateGui::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventNewOpenLoopNotification::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventRefreshOverview::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventAcceptOpenLoopChange::class.java)).thenReturn(emptyFlow())
        sut = LoopActionViewModel(loop, activePlugin, profileFunction, rh, rxBus)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has no action available`() {
        val state = sut.uiState.value
        assertThat(state.actionAvailable).isFalse()
        assertThat(state.request).isEmpty()
        assertThat(state.reason).isEmpty()
    }
}
