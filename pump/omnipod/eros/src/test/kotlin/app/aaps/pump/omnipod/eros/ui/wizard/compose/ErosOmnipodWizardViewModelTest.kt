package app.aaps.pump.omnipod.eros.ui.wizard.compose

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
internal class ErosOmnipodWizardViewModelTest {

    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var logger: AAPSLogger
    @Mock private lateinit var aapsSchedulers: AapsSchedulers

    private val aapsOmnipodManager: AapsOmnipodErosManager = mock()
    private val podStateManager: AapsErosPodStateManager = mock()
    private val pumpEnactResultProvider: Provider<PumpEnactResult> = mock()

    private lateinit var sut: ErosOmnipodWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // init { viewModelScope.launch { ... _ready = true } } is deferred by StandardTestDispatcher, so
        // construction touches no collaborators; the base + concrete state stay at defaults.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = ErosOmnipodWizardViewModel(
            aapsOmnipodManager, podStateManager, commandQueue, pumpSync, insulinManager, profileFunction,
            profileRepository, persistenceLayer, preferences, pumpEnactResultProvider, logger, aapsSchedulers
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state has no current step and is not ready`() {
        assertThat(sut.currentStep.value).isNull()
        assertThat(sut.totalSteps.value).isEqualTo(0)
        assertThat(sut.actionState.value).isEqualTo(ActionState.Idle)
        assertThat(sut.ready.value).isFalse()
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
    }

    @Test
    fun `initializeDeactivation builds the three-step flow and advances`() {
        sut.initializeDeactivation()

        assertThat(sut.totalSteps.value).isEqualTo(3)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.START_POD_DEACTIVATION)

        sut.moveToNext()
        assertThat(sut.currentStepIndex.value).isEqualTo(1)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.DEACTIVATE_POD)
    }

    @Test
    fun `initializeActivation LONG builds the five-step flow without optional steps`() {
        // No profile gate (default), single insulin -> no SELECT_INSULIN, SiteRotationManagePump=false (mock).
        sut.initializeActivation(ActivationType.LONG)

        assertThat(sut.totalSteps.value).isEqualTo(5)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.START_POD_ACTIVATION)
    }

    @Test
    fun `selectInsulin and site setters update the state`() {
        val iCfg = mock<ICfg>()
        sut.selectInsulin(iCfg)
        assertThat(sut.selectedInsulin.value).isEqualTo(iCfg)

        sut.updateSiteLocation(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        sut.updateSiteArrow(TE.Arrow.DOWN)
        assertThat(sut.getSelectedSiteLocation()).isEqualTo(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        assertThat(sut.getSelectedSiteArrow()).isEqualTo(TE.Arrow.DOWN)
    }

    @Test
    fun `pod alarm and activation-time queries read the pod state manager`() {
        assertThat(sut.isPodInAlarm()).isFalse()
        assertThat(sut.isPodActivationTimeExceeded()).isFalse()
    }

    @Test
    fun `getTextForStep returns zero for the profile gate step`() {
        assertThat(sut.getTextForStep(OmnipodWizardStep.PROFILE_GATE)).isEqualTo(0)
    }
}
