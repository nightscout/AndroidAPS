package app.aaps.ui.compose.wizardDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class WizardDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var runningModeGuard: RunningModeGuard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var wizardExecutor: WizardExecutor
    @Mock private lateinit var rxBus: RxBus

    private val bolusWizardProvider: () -> BolusWizard = mock()

    private lateinit var sut: WizardDialogViewModel

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // init { viewModelScope.launch { initialize() } } is deferred by StandardTestDispatcher, so construction
        // touches no collaborators and the internal BolusWizard stays null; the pure state flips below don't need it.
        // The scheduler is shared with runTest below, so the first advanceUntilIdle() in a test body runs
        // initialize() before the refresh queued after it, and drives everything the view model launches.
        // Stubbed before construction: init { } queues initialize() on the shared scheduler, so it runs
        // before any test body. With no profile store it returns straight away, which is all these tests need.
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(null))
        Dispatchers.setMain(mainDispatcher)
        sut = WizardDialogViewModel(
            SavedStateHandle(), bolusWizardProvider, constraintChecker, profileFunction, profileUtil,
            profileRepository, activePlugin, ch, iobCobCalculator, persistenceLayer, preferences, config,
            rh, dateUtil, decimalFormatter, aapsLogger, runningModeGuard, automation, wizardExecutor, rxBus,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `updateNotes and toggleAlarm update the state`() {
        sut.updateNotes("wizard note")
        sut.toggleAlarm(true)

        assertThat(sut.uiState.value.notes).isEqualTo("wizard note")
        assertThat(sut.uiState.value.alarmChecked).isTrue()
    }

    /**
     * `initialize()` and `recalculateSuspend()` both return as soon as there is no profile store, so
     * these tests reach the percentage decision without standing up the whole wizard.
     */
    private fun stubForRefresh(storedPercentage: Int) {
        whenever(preferences.get(BooleanNonKey.WizardIncludeTrend)).thenReturn(false)
        whenever(preferences.get(BooleanNonKey.WizardIncludeCob)).thenReturn(false)
        whenever(preferences.get(BooleanKey.OverviewUseBolusAdvisor)).thenReturn(false)
        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(storedPercentage)
        whenever(preferences.get(IntKey.OverviewResetBolusPercentageTime)).thenReturn(30)
        whenever(dateUtil.now()).thenReturn(NOW)
    }

    private fun lastGlucoseValueAt(timestamp: Long) =
        GV(timestamp = timestamp, raw = null, value = 100.0, trendArrow = TrendArrow.FLAT, noise = null, sourceSensor = SourceSensor.UNKNOWN)

    @Test
    fun `dismissing the settings sheet keeps the percentage the user set in the dialog`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        sut.updatePercentage(150)
        advanceUntilIdle()
        assertThat(sut.uiState.value.percentage).isEqualTo(150)

        // The sheet has no control for the per bolus percentage, so dismissing it must not touch it.
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(150)
    }

    /**
     * The first read has nothing to compare against, so it only remembers the stored value. Without
     * this, a dismissal before the dialog finished starting up would apply the stored percentage over
     * whatever the slider held.
     */
    @Test
    fun `the first read of the stored percentage does not touch the slider`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 150)
        val before = sut.uiState.value.percentage

        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(before)
    }

    @Test
    fun `a percentage really changed in the settings sheet is applied`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(120)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(lastGlucoseValueAt(NOW))
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(120)
    }

    @Test
    fun `a percentage above 100 is dropped when the last glucose value is too old`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        // Older than OverviewResetBolusPercentageTime, so the stored percentage must not be re-applied.
        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(150)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(lastGlucoseValueAt(NOW - T.mins(45).msecs()))
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(100)
    }

    @Test
    fun `a percentage above 100 is dropped when there is no glucose value at all`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(150)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(null)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(100)
    }

    private companion object {

        const val NOW = 1_700_000_000_000L
    }
}
