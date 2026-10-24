package app.aaps.ui.compose.extendedBolusDialog

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpWithConcentration
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExtendedBolusDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var config: Config
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private val pump: PumpWithConcentration = mock()
    private val pumpDescription: PumpDescription = mock()
    private val maxConstraint: Constraint<Double> = mock()

    private lateinit var sut: ExtendedBolusDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(maxConstraint.value()).thenReturn(10.0)
        whenever(constraintChecker.getMaxExtendedBolusAllowed()).thenReturn(maxConstraint)
        sut = ExtendedBolusDialogViewModel(
            constraintChecker, activePlugin, config, rh, batchExecutor, rxBus,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `updateInsulin and updateDuration update the state`() {
        sut.updateInsulin(2.0)
        sut.updateDuration(60.0)

        assertThat(sut.uiState.value.insulin).isEqualTo(2.0)
        assertThat(sut.uiState.value.durationMinutes).isEqualTo(60.0)
    }

    @Test
    fun `acceptLoopStopWarning marks the warning accepted`() {
        sut.acceptLoopStopWarning()
        assertThat(sut.uiState.value.loopStopWarningAccepted).isTrue()
    }
}
