package app.aaps.ui.compose.treatmentDialog

import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
    @Mock private lateinit var commandQueue: CommandQueue

    private val smbBeingDelivered = MutableStateFlow(false)
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
        whenever(commandQueue.smbBeingDelivered).thenReturn(smbBeingDelivered)
        sut = createSut()
    }

    private fun createSut() = TreatmentDialogViewModel(
        constraintChecker, activePlugin, ch, config, decimalFormatter, rh,
        profileFunction, hardLimits, loop, batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher()), commandQueue
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

    // #5381: the queue would refuse the bolus after the dialog has closed, so OK waits here, with the input kept.
    @Test
    fun `OK waits for an SMB only when the pump gives insulin`() {
        whenever(pump.isInitialized()).thenReturn(true)
        sut = createSut()
        smbBeingDelivered.value = true
        mainDispatcher.scheduler.advanceUntilIdle()

        sut.updateCarbs(30)
        assertThat(sut.uiState.value.waitsForSmb).isFalse()
        sut.updateInsulin(2.0)
        assertThat(sut.uiState.value.waitsForSmb).isTrue()

        smbBeingDelivered.value = false
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(sut.uiState.value.waitsForSmb).isFalse()
        assertThat(sut.uiState.value.insulin).isEqualTo(2.0)
    }

    @Test
    fun `a dose only recorded does not wait for an SMB`() {
        val recorded = TreatmentDialogUiState(insulin = 2.0, forcedRecordOnly = true, smbBeingDelivered = true)
        assertThat(recorded.waitsForSmb).isFalse()
    }

    @Test
    fun `cancel drops the prepared treatment`() {
        sut.discard(9L)
        verify(batchExecutor).discard(9L)
    }

    // Nothing was sent to the pump, so the bolus-error alarm would be wrong: a plain message with the master's text.
    @Test
    fun `the master reports its own refusal as a message, not as the bolus-error alarm`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(rh.gs(InterfacesStrings.bolus)).thenReturn("Bolus")
        whenever(batchExecutor.prepare(any(), any(), anyOrNull())).thenReturn(ActionProgress.Rejected(FailureReason.ExecutionFailed, PUMP_NOT_AVAILABLE))
        val shown = mutableListOf<TreatmentDialogViewModel.SideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.sideEffect.collect { shown += it } }
        sut.updateInsulin(1.0)

        sut.prepareAndConfirm()

        verify(rxBus).send(EventShowDialog.Ok(title = "Bolus", message = PUMP_NOT_AVAILABLE))
        assertThat(shown).isEmpty()
    }

    private companion object {

        const val PUMP_NOT_AVAILABLE = "Pump not available"
    }
}
