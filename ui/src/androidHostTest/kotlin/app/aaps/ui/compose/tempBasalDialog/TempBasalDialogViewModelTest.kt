package app.aaps.ui.compose.tempBasalDialog

import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
internal class TempBasalDialogViewModelTest {

    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var config: Config
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: TempBasalDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // init { viewModelScope.launch { initialize() } } is deferred by StandardTestDispatcher (no advance),
        // so construction is clean and we test the synchronous setters on the default state.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = TempBasalDialogViewModel(
            profileFunction, activePlugin, config, rh, batchExecutor, rxBus,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `basal and duration setters update the state`() {
        sut.updateBasalPercent(150.0)
        sut.updateBasalAbsolute(1.5)
        sut.updateDuration(60.0)

        val state = sut.uiState.value
        assertThat(state.basalPercent).isEqualTo(150.0)
        assertThat(state.basalAbsolute).isEqualTo(1.5)
        assertThat(state.durationMinutes).isEqualTo(60.0)
    }
}
