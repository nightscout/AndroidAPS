package app.aaps.ui.compose.wizardDialog

import androidx.lifecycle.SavedStateHandle
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
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
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
import javax.inject.Provider

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

    private val bolusWizardProvider: Provider<BolusWizard> = mock()

    private lateinit var sut: WizardDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // init { viewModelScope.launch { initialize() } } is deferred by StandardTestDispatcher, so construction
        // touches no collaborators and the internal BolusWizard stays null; the pure state flips below don't need it.
        Dispatchers.setMain(StandardTestDispatcher())
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

    @Test
    fun `advanced and calculation sections toggle open and closed`() {
        val advanced0 = sut.uiState.value.advancedExpanded
        sut.toggleAdvancedExpanded()
        assertThat(sut.uiState.value.advancedExpanded).isEqualTo(!advanced0)

        val calc0 = sut.uiState.value.calculationExpanded
        sut.toggleCalculationExpanded()
        assertThat(sut.uiState.value.calculationExpanded).isEqualTo(!calc0)
    }
}
