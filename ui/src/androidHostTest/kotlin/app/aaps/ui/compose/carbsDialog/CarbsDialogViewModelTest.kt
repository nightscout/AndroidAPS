package app.aaps.ui.compose.carbsDialog

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
}
