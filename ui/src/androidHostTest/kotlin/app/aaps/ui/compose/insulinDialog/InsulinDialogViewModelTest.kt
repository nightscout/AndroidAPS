package app.aaps.ui.compose.insulinDialog

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
internal class InsulinDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var config: Config
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var hardLimits: HardLimits
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var nsClient: NsClient

    private val role = MutableStateFlow(ClientRole.Admin)
    private val pump: PumpWithConcentration = mock()
    private val pumpDescription: PumpDescription = mock()
    private val maxBolusConstraint: Constraint<Double> = mock()

    private lateinit var sut: InsulinDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers only the loop-mode check launched in init; the synchronous init body
        // below still runs at construction, so its collaborators must be stubbed.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpDescription.bolusStep).thenReturn(0.1)
        whenever(maxBolusConstraint.value()).thenReturn(10.0)
        whenever(constraintChecker.getMaxBolusAllowed()).thenReturn(maxBolusConstraint)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(insulinManager.insulins).thenReturn(arrayListOf<ICfg>())
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(0.0)
        // ttTargetMgdl/ttDurationMinutes parse this preset string in init; "[]" -> empty -> use defaults.
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        whenever(nsClient.clientRole).thenReturn(role)
        sut = create()
    }

    private fun create() = InsulinDialogViewModel(
        constraintChecker, profileFunction, profileUtil, activePlugin, ch, insulinManager,
        config, automation, decimalFormatter, loop, preferences, rh, dateUtil, hardLimits, batchExecutor,
        rxBus, CoroutineScope(UnconfinedTestDispatcher()), nsClient
    )

    @Test
    fun `the eating soon target is offered to a role that may set one`() {
        assertThat(sut.uiState.value.canSetTempTarget).isTrue()
    }

    // A meal assistant may give insulin but not set a temp target; with the switch on, the master refuses the bolus too.
    @Test
    fun `a meal assistant is not offered the eating soon target`() {
        role.value = ClientRole.MealAssistant

        assertThat(create().uiState.value.canSetTempTarget).isFalse()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `updateInsulin clamps to the max bolus`() {
        sut.updateInsulin(20.0)
        assertThat(sut.uiState.value.insulin).isEqualTo(10.0)
    }

    @Test
    fun `updateInsulin clamps negatives to zero`() {
        sut.updateInsulin(-5.0)
        assertThat(sut.uiState.value.insulin).isEqualTo(0.0)
    }

    @Test
    fun `updateTimeOffset clamps to plus or minus 12 hours`() {
        sut.updateTimeOffset(1000)
        assertThat(sut.uiState.value.timeOffsetMinutes).isEqualTo(720)

        sut.updateTimeOffset(-1000)
        assertThat(sut.uiState.value.timeOffsetMinutes).isEqualTo(-720)
    }

    // ---- a record-only dose (a pen) is not given by the pump, so it is not put on the pump's grid ----

    /** The amount the dialog sends for [insulin] at U200, where the pump steps by 0.2 U of insulin. */
    private fun sentAmount(insulin: Double, recordOnly: Boolean): Double = runBlocking {
        whenever(ch.bolusStep(any())).thenReturn(0.2)
        whenever(batchExecutor.prepare(any(), any(), anyOrNull())).thenReturn(ActionProgress.Applied)
        sut.updateRecordOnly(recordOnly)
        sut.updateInsulin(insulin)
        sut.prepareAndConfirm()
        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), any(), anyOrNull())
        actions.firstValue.filterIsInstance<BatchAction.Bolus>().single().insulin
    }

    @Test
    fun `a record-only dose is recorded as typed`() {
        // Floored, the 1.5 U pen dose was saved as 1.4 U and IOB was too low.
        assertThat(sentAmount(1.5, recordOnly = true)).isEqualTo(1.5)
    }

    @Test
    fun `a dose for the pump is floored to the pump step`() {
        assertThat(sentAmount(1.5, recordOnly = false)).isWithin(1e-9).of(1.4)
    }

    @Test
    fun `the quick add buttons keep a record-only dose off the pump grid too`() {
        whenever(ch.bolusStep(any())).thenReturn(0.2)
        sut.updateRecordOnly(true)
        sut.addInsulin(1.5)
        assertThat(sut.uiState.value.insulin).isEqualTo(1.5)

        sut.updateRecordOnly(false)
        sut.updateInsulin(0.0)
        sut.addInsulin(1.5)
        assertThat(sut.uiState.value.insulin).isWithin(1e-9).of(1.4)
    }

    @Test
    fun `a record-only dose is shown with two decimals`() {
        sut.updateRecordOnly(true)
        assertThat(sut.uiState.value.displayStep).isEqualTo(0.01)

        sut.updateRecordOnly(false)
        assertThat(sut.uiState.value.displayStep).isEqualTo(sut.uiState.value.bolusStep)
    }

    @Test
    fun `updateEatingSoonTt and updateNotes update the state`() {
        sut.updateEatingSoonTt(true)
        sut.updateNotes("correction")

        assertThat(sut.uiState.value.eatingSoonTtChecked).isTrue()
        assertThat(sut.uiState.value.notes).isEqualTo("correction")
    }
}
