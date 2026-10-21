package app.aaps.ui.compose.calibrationDialog

import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The calibration dialog only shows what the master decides: it sends the fingerstick value, renders the master's
 * confirmation, and commits it. Storing the calibration on this device is the master's job (see `MasterRecordWriter`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CalibrationDialogViewModelTest {

    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var config: Config
    @Mock private lateinit var calibration: Calibration

    private lateinit var dispatcher: TestDispatcher
    private lateinit var sut: CalibrationDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // refreshPreconditions() is launched on viewModelScope -> deferred by StandardTestDispatcher.
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MGDL)
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(0.0)
        whenever(profileUtil.convertToMgdl(any(), any())).thenAnswer { it.getArgument<Double>(0) }
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(CAL::class)).thenReturn(emptyFlow())
        whenever(activePlugin.activeCalibration).thenReturn(calibration)
        runBlocking { whenever(calibration.checkPreconditions()).thenReturn(AddEntryResult.Accepted) }
        sut = CalibrationDialogViewModel(
            profileUtil, glucoseStatusProvider, activePlugin, persistenceLayer, dateUtil, generatedTextResolver(), decimalFormatter, batchExecutor, config
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no action when bg is zero`() {
        assertThat(sut.uiState.value.bg).isEqualTo(0.0)
        assertThat(sut.hasAction()).isFalse()
    }

    @Test
    fun `updateBg sets the value and enables the action`() {
        sut.updateBg(120.0)

        assertThat(sut.uiState.value.bg).isEqualTo(120.0)
        assertThat(sut.hasAction()).isTrue()
    }

    @Test
    fun `the calibration goes to the master and its confirmation is shown`() = runTest(dispatcher) {
        val lines = listOf(ConfirmationLine(ConfirmationRole.PRIMARY, "Calibration: 120 mg/dl"))
        whenever(batchExecutor.prepare(any(), eq(Sources.CalibrationDialog), any())).thenReturn(ActionProgress.Prepared(7L, lines))
        sut.updateBg(120.0)

        sut.requestCalibration()
        advanceUntilIdle()

        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), eq(Sources.CalibrationDialog), any())
        assertThat(actions.firstValue).containsExactly(BatchAction.Calibration(120.0))
        assertThat(sut.confirmation.value).isEqualTo(CalibrationDialogViewModel.Confirmation(7L, lines))
    }

    @Test
    fun `confirming commits the master's prepared calibration and closes the dialog`() = runTest(dispatcher) {
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Prepared(7L, emptyList()))
        whenever(batchExecutor.commit(eq(7L), eq(Sources.CalibrationDialog), any(), any())).thenReturn(ActionProgress.Applied)
        sut.updateBg(120.0)
        sut.requestCalibration()
        advanceUntilIdle()

        sut.confirmCalibration()
        advanceUntilIdle()

        verify(batchExecutor).commit(eq(7L), eq(Sources.CalibrationDialog), any(), any())
        assertThat(sut.confirmation.value).isNull()
        assertThat(sut.sideEffect.replayCache).containsExactly(CalibrationDialogViewModel.SideEffect.EntryAccepted)
    }

    @Test
    fun `a refusal from the master is shown on the master`() = runTest(dispatcher) {
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Rejected(FailureReason.ExecutionFailed, "BG is changing too fast"))
        sut.updateBg(120.0)

        sut.requestCalibration()
        advanceUntilIdle()

        assertThat(sut.confirmation.value).isNull()
        assertThat(sut.sideEffect.replayCache).containsExactly(CalibrationDialogViewModel.SideEffect.EntryRejected("BG is changing too fast"))
    }

    @Test
    fun `cancelling the confirmation commits nothing`() = runTest(dispatcher) {
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Prepared(7L, emptyList()))
        sut.updateBg(120.0)
        sut.requestCalibration()
        advanceUntilIdle()

        sut.dismissCalibration()
        sut.confirmCalibration()
        advanceUntilIdle()

        verify(batchExecutor, never()).commit(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `a sensor change goes to the master as a therapy event`() = runTest(dispatcher) {
        whenever(dateUtil.now()).thenReturn(1_000L)
        whenever(batchExecutor.prepare(any(), eq(Sources.SensorInsert), any())).thenReturn(ActionProgress.Prepared(9L, emptyList()))
        whenever(batchExecutor.commit(eq(9L), eq(Sources.SensorInsert), any(), any())).thenReturn(ActionProgress.Applied)

        sut.markSensorChangeNow()
        advanceUntilIdle()

        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), eq(Sources.SensorInsert), any())
        assertThat(actions.firstValue).containsExactly(BatchAction.TherapyEvent(teType = TE.Type.SENSOR_CHANGE, timestamp = 1_000L, source = Sources.SensorInsert))
        verify(batchExecutor).commit(eq(9L), eq(Sources.SensorInsert), any(), any())
    }
}
