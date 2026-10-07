package app.aaps.ui.compose.carbsDialog

import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
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

    private lateinit var sut: CarbsDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched initialize() coroutine (no advanceUntilIdle),
        // so construction stays clean and we test the synchronous update methods against the default state.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = CarbsDialogViewModel(
            constraintChecker, profileUtil, iobCobCalculator, glucoseStatusProvider, automation,
            batchExecutor, persistenceLayer, preferences, config, rh, dateUtil, rxBus,
            CoroutineScope(UnconfinedTestDispatcher())
        )
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
