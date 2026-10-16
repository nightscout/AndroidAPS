package app.aaps.ui.compose.treatmentDialog

import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class TreatmentDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var config: Config
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var hardLimits: HardLimits
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private val pump: PumpWithConcentration = mock()
    private val pumpDescription: PumpDescription = mock()
    private val maxBolus: Constraint<Double> = mock()
    private val maxCarbs: Constraint<Int> = mock()

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var sut: TreatmentDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(mainDispatcher)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpDescription.bolusStep).thenReturn(0.1)
        whenever(ch.bolusStep(any())).thenReturn(0.1)
        whenever(maxBolus.value()).thenReturn(10.0)
        whenever(constraintChecker.getMaxBolusAllowed()).thenReturn(maxBolus)
        whenever(maxCarbs.value()).thenReturn(100)
        whenever(constraintChecker.getMaxCarbsAllowed()).thenReturn(maxCarbs)
        runBlocking { whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP) }
        sut = createSut()
    }

    private fun createSut() = TreatmentDialogViewModel(
        constraintChecker, activePlugin, ch, config, decimalFormatter, rh,
        profileFunction, hardLimits, loop, batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher())
    )

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** The insulin of the one bolus the dialog asks the master to prepare. */
    private suspend fun preparedInsulin(): Double {
        sut.prepareAndConfirm()
        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), any(), anyOrNull())
        return (actions.firstValue.single() as BatchAction.Bolus).insulin
    }

    @Test
    fun `updateInsulin and updateCarbs update the state`() {
        sut.updateInsulin(2.5)
        sut.updateCarbs(30)

        assertThat(sut.uiState.value.insulin).isEqualTo(2.5)
        assertThat(sut.uiState.value.carbs).isEqualTo(30)
    }

    // The pump is not initialized here, so the dose is only recorded. The pump does not give it, so its step
    // does not apply: a pen can give 1.33 U.
    @Test
    fun `a forced record-only dose is kept as typed`() = runBlocking<Unit> {
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(sut.uiState.value.forcedRecordOnly).isTrue() // the premise

        sut.updateInsulin(1.33)

        assertThat(preparedInsulin()).isEqualTo(1.33)
    }

    @Test
    fun `a dose the pump gives is floored to its step`() = runBlocking<Unit> {
        whenever(pump.isInitialized()).thenReturn(true)
        sut = createSut()
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(sut.uiState.value.forcedRecordOnly).isFalse() // the premise

        sut.updateInsulin(1.33)

        assertThat(preparedInsulin()).isWithin(1e-9).of(1.3)
    }
}
