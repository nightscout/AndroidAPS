package app.aaps.ui.compose.carbsDialog

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class CarbsDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var nsClient: NsClient

    private val main = StandardTestDispatcher()
    private val role = MutableStateFlow(ClientRole.Admin)
    private lateinit var sut: CarbsDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched initialize() coroutine (no advanceUntilIdle),
        // so construction stays clean and we test the synchronous update methods against the default state.
        Dispatchers.setMain(main)
        whenever(nsClient.clientRole).thenReturn(role)
        sut = create()
    }

    private fun create() = CarbsDialogViewModel(
        constraintChecker, profileUtil, iobCobCalculator, glucoseStatusProvider, automation,
        batchExecutor, persistenceLayer, preferences, config, rh, dateUtil, rxBus,
        CoroutineScope(UnconfinedTestDispatcher()), nsClient
    )

    /**
     * Opens the dialog with [role] at a BG of 60 mg/dL: the case where the hypo target is ticked for the user.
     * [runningTarget] is the temp target that runs at that time, none by default.
     */
    private fun openedWhileLow(role: ClientRole, runningTarget: TT? = null): CarbsDialogUiState {
        this.role.value = role
        runBlocking { whenever(persistenceLayer.getTemporaryTargetActiveAt(any())).thenReturn(runningTarget) }
        val maxCarbs = mock<Constraint<Int>> { on { value() } doReturn 50 }
        whenever(constraintChecker.getMaxCarbsAllowed()).thenReturn(maxCarbs)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MGDL)
        val ads = mock<AutosensDataStore> { on { actualBg() } doReturn InMemoryGlucoseValue(value = 60.0) }
        whenever(iobCobCalculator.ads).thenReturn(ads)
        runBlocking { whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(0L, 0.0, 0.0)) }
        // ttTargetMgdl/ttDurationMinutes parse this preset string; "[]" -> the defaults.
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        val opened = create()
        main.scheduler.advanceUntilIdle()
        return opened.uiState.value
    }

    @Test
    fun `a low ticks the hypo target for a role that may set one`() {
        val state = openedWhileLow(ClientRole.Caregiver)

        assertThat(state.canSetTempTarget).isTrue()
        assertThat(state.hypoTtChecked).isTrue()
    }

    // The carbs for a low are what a meal assistant is for. With the hypo target ticked, the master would refuse both.
    @Test
    fun `a meal assistant treating a low is not given a temp target`() {
        val state = openedWhileLow(ClientRole.MealAssistant)

        assertThat(state.canSetTempTarget).isFalse()
        assertThat(state.hypoTtChecked).isFalse()
    }

    /** A temp target that runs for three hours, longer than the hypo target. */
    private fun longTarget(targetMgdl: Double) =
        TT(timestamp = 0L, reason = TT.Reason.CUSTOM, highTarget = targetMgdl, lowTarget = targetMgdl, duration = 3 * 60 * 60 * 1000L)

    // A long high target is not replaced by the hypo target. 5.5 mmol/L is 99.09 mg/dL with the 18.01559 factor,
    // and it is the normal target, not a high one, so the hypo target is still ticked.
    @Test
    fun `a long 5_5 mmol target still lets the hypo target be ticked`() {
        val state = openedWhileLow(ClientRole.Caregiver, runningTarget = longTarget(5.5 * Constants.MMOLL_TO_MGDL))

        assertThat(state.hypoTtChecked).isTrue()
    }

    @Test
    fun `a long 5_6 mmol target is not replaced by the hypo target`() {
        val state = openedWhileLow(ClientRole.Caregiver, runningTarget = longTarget(5.6 * Constants.MMOLL_TO_MGDL))

        assertThat(state.hypoTtChecked).isFalse()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `hypo temp-target is mutually exclusive with the others`() {
        sut.updateEatingSoonTt(true)
        sut.updateHypoTt(true)

        val state = sut.uiState.value
        assertThat(state.hypoTtChecked).isTrue()
        assertThat(state.eatingSoonTtChecked).isFalse()
        assertThat(state.activityTtChecked).isFalse()
    }

    @Test
    fun `eating-soon temp-target clears hypo and activity`() {
        sut.updateActivityTt(true)
        sut.updateEatingSoonTt(true)

        val state = sut.uiState.value
        assertThat(state.eatingSoonTtChecked).isTrue()
        assertThat(state.hypoTtChecked).isFalse()
        assertThat(state.activityTtChecked).isFalse()
    }

    @Test
    fun `activity temp-target clears hypo and eating-soon`() {
        sut.updateHypoTt(true)
        sut.updateActivityTt(true)

        val state = sut.uiState.value
        assertThat(state.activityTtChecked).isTrue()
        assertThat(state.hypoTtChecked).isFalse()
        assertThat(state.eatingSoonTtChecked).isFalse()
    }

    @Test
    fun `updateNotes and flag setters update the state`() {
        sut.updateNotes("late dinner")
        sut.updateAlarm(true)
        sut.updateBolusReminder(true)

        val state = sut.uiState.value
        assertThat(state.notes).isEqualTo("late dinner")
        assertThat(state.alarmChecked).isTrue()
        assertThat(state.bolusReminderChecked).isTrue()
    }

    /** `glucose + 3 * delta < 70` is the "heading low" test the reminder row is gated on. */
    private fun glucoseHeadingLow(headingLow: Boolean) {
        // Built before the whenever(), because Mockito cannot have one stubbing started inside another.
        val status = mock<GlucoseStatus> {
            on { glucose } doReturn if (headingLow) 80.0 else 120.0
            on { delta } doReturn if (headingLow) -5.0 else 0.0
        }
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(status)
    }

    @Test
    fun `turning the bolus reminder on in the settings sheet shows the row at once`() {
        whenever(preferences.get(BooleanKey.OverviewUseBolusReminder)).thenReturn(true)
        glucoseHeadingLow(true)

        sut.refreshCarbsButtons()

        assertThat(sut.uiState.value.showBolusReminder).isTrue()
    }

    @Test
    fun `the reminder row stays hidden when the glucose is not heading low`() {
        whenever(preferences.get(BooleanKey.OverviewUseBolusReminder)).thenReturn(true)
        glucoseHeadingLow(false)

        sut.refreshCarbsButtons()

        assertThat(sut.uiState.value.showBolusReminder).isFalse()
    }

    @Test
    fun `turning the bolus reminder off hides the row and clears the tick`() {
        whenever(preferences.get(BooleanKey.OverviewUseBolusReminder)).thenReturn(true)
        glucoseHeadingLow(true)
        sut.refreshCarbsButtons()
        sut.updateBolusReminder(true)
        assertThat(sut.uiState.value.bolusReminderChecked).isTrue()

        whenever(preferences.get(BooleanKey.OverviewUseBolusReminder)).thenReturn(false)
        sut.refreshCarbsButtons()

        val state = sut.uiState.value
        assertThat(state.showBolusReminder).isFalse()
        assertThat(state.bolusReminderChecked).isFalse()
    }
}
