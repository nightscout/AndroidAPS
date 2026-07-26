package app.aaps.pump.omnipod.dash.ui.wizard.compose

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
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
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * Unit test for [DashOmnipodWizardViewModel]'s synchronous, pure wizard-navigation and selection
 * logic (inherited from OmnipodWizardViewModel and the Dash pod-state overrides).
 *
 * The VM's init{} launches a coroutine (loadInsulins / site-rotation / profile-gate) via
 * viewModelScope. A StandardTestDispatcher as Main DEFERS that coroutine (no advanceUntilIdle here),
 * so construction stays clean and every assertion runs against the deterministic default state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DashOmnipodWizardViewModelTest {

    @Mock private lateinit var omnipodManager: OmnipodDashManager
    @Mock private lateinit var podStateManager: OmnipodDashPodStateManager
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var history: DashHistory
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>
    @Mock private lateinit var logger: AAPSLogger
    @Mock private lateinit var aapsSchedulers: AapsSchedulers

    private lateinit var sut: DashOmnipodWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        sut = DashOmnipodWizardViewModel(
            omnipodManager, podStateManager, preferences, rh, history, commandQueue,
            notificationManager, pumpSync, fabricPrivacy, insulinManager, profileFunction,
            profileRepository, persistenceLayer, pumpEnactResultProvider, logger, aapsSchedulers
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultState_isIdleAndUninitialized() {
        assertThat(sut.currentStep.value).isNull()
        assertThat(sut.totalSteps.value).isEqualTo(0)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.canGoBack.value).isFalse()
        assertThat(sut.actionState.value).isEqualTo(ActionState.Idle)
        assertThat(sut.ready.value).isFalse()
        assertThat(sut.availableInsulins.value).isEmpty()
        assertThat(sut.selectedInsulin.value).isNull()
        assertThat(sut.showInsulinStep).isFalse()
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
    }

    @Test
    fun podStateQueries_haveDashDefaults() {
        assertThat(sut.isPodInAlarm()).isFalse()
        assertThat(sut.isPodActivationTimeExceeded()).isFalse()
        assertThat(sut.isPodDeactivatable()).isTrue()
    }

    @Test
    fun profileGateStep_hasNoBodyTextResource() {
        // PROFILE_GATE uses its own composable and returns the literal 0 (deterministic, no R lookup).
        assertThat(sut.getTextForStep(OmnipodWizardStep.PROFILE_GATE)).isEqualTo(0)
    }

    @Test
    fun initializeDeactivation_buildsThreeStepFlow() {
        sut.initializeDeactivation()

        assertThat(sut.totalSteps.value).isEqualTo(3)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.START_POD_DEACTIVATION)
        assertThat(sut.canGoBack.value).isFalse()

        sut.moveToNext()
        assertThat(sut.currentStepIndex.value).isEqualTo(1)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.DEACTIVATE_POD)

        sut.moveToNext()
        assertThat(sut.currentStepIndex.value).isEqualTo(2)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.POD_DEACTIVATED)
    }

    @Test
    fun initializeActivationLong_buildsFullFlow_withoutOptionalSteps() {
        // No extra profile gate, no insulin step (0 insulins), no site-location step.
        whenever(preferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(false)

        sut.initializeActivation(ActivationType.LONG)

        assertThat(sut.totalSteps.value).isEqualTo(5)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.START_POD_ACTIVATION)

        sut.moveToNext()
        assertThat(sut.currentStep.value).isEqualTo(OmnipodWizardStep.INITIALIZE_POD)
    }

    @Test
    fun selectInsulin_updatesSelectedInsulin() {
        val insulin: ICfg = mock()

        sut.selectInsulin(insulin)

        assertThat(sut.selectedInsulin.value).isSameInstanceAs(insulin)
    }

    @Test
    fun selectProfile_updatesSelectedProfile() {
        sut.selectProfile("Default")

        assertThat(sut.selectedProfile.value).isEqualTo("Default")
    }

    @Test
    fun updateSiteLocationAndArrow_areReflectedInSelectors() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)
        sut.updateSiteArrow(TE.Arrow.UP)

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.UP)
        assertThat(sut.getSelectedSiteLocation()).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.getSelectedSiteArrow()).isEqualTo(TE.Arrow.UP)
    }
}
