package app.aaps.pump.omnipod.omnipod5.ui.wizard.compose
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandInitializePod
import app.aaps.pump.omnipod.common.queue.command.CommandInsertCannula
import app.aaps.pump.omnipod.omnipod5.O5PumpPlugin
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.shared.tests.rx.TestAapsSchedulers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Mirrors [app.aaps.pump.omnipod.dash.ui.wizard.compose.DashOmnipodWizardViewModelTest]'s
 * pattern for O5's concrete [O5OmnipodWizardViewModel] - covers the synchronous,
 * pure wizard-navigation and selection logic inherited from
 * [OmnipodWizardViewModel] plus the O5-specific overrides. The steps that talk to the pod are
 * commands of the queue; what they do with the pod is tested in
 * [app.aaps.pump.omnipod.omnipod5.O5PodActivationTest].
 *
 * The VM's `init{}` launches a coroutine via `viewModelScope`. A `StandardTestDispatcher`
 * as Main defers that coroutine (no `advanceUntilIdle()` here), so construction stays
 * clean and every assertion runs against the deterministic default state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class O5OmnipodWizardViewModelTest {

    @Mock private lateinit var podStateManager: O5PodStateManager
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    private val enactResult: PumpEnactResult = mock<PumpEnactResult>(defaultAnswer = Answers.RETURNS_SELF).also {
        whenever(it.comment).thenReturn("error")
    }
    private val pumpEnactResultProvider: () -> PumpEnactResult = { enactResult }
    @Mock private lateinit var logger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    private val aapsSchedulers: AapsSchedulers = TestAapsSchedulers()

    private lateinit var sut: O5OmnipodWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        sut = O5OmnipodWizardViewModel(
            mock<O5PumpPlugin>(), podStateManager, preferences, rh, commandQueue,
            insulinManager, persistenceLayer,
            profileFunction, profileRepository, pumpEnactResultProvider, logger, aapsSchedulers, rxBus
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
    fun pumpSource_isOmnipod5() {
        assertThat(sut.pumpSource).isEqualTo(Sources.Omnipod5)
    }

    @Test
    fun isPodInAlarm_reflectsAlarmType_unlikeDashsUnimplementedStub() {
        whenever(podStateManager.alarmType).thenReturn(null)
        assertThat(sut.isPodInAlarm()).isFalse()

        whenever(podStateManager.alarmType).thenReturn(AlarmType.ALARM_EMPTY_RESERVOIR)
        assertThat(sut.isPodInAlarm()).isTrue()
    }

    @Test
    fun podStateQueries_reflectActivationTimeoutAndPairingState() {
        whenever(podStateManager.isPodActivationTimeExceeded).thenReturn(true)
        whenever(podStateManager.ltk).thenReturn(null)

        assertThat(sut.isPodActivationTimeExceeded()).isTrue()
        assertThat(sut.isPodDeactivatable()).isFalse()

        whenever(podStateManager.ltk).thenReturn(ByteArray(16))
        whenever(podStateManager.controllerId).thenReturn(0x11223340L)
        whenever(podStateManager.podId).thenReturn(0x11223341L)

        assertThat(sut.isPodDeactivatable()).isTrue()
    }

    @Test
    fun podStateQueries_doNotReportActivationTimeoutForOtherStates() {
        whenever(podStateManager.isPodActivationTimeExceeded).thenReturn(false)

        assertThat(sut.isPodActivationTimeExceeded()).isFalse()
    }

    @Test
    fun profileGateStep_hasNoBodyTextResource() {
        assertThat(sut.getTextForStep(OmnipodWizardStep.PROFILE_GATE)).isEqualTo(0)
    }

    @Test
    fun everyWizardStep_hasATitleAndTextResourceMapping() {
        for (step in OmnipodWizardStep.entries) {
            sut.getTitleForStep(step)
            sut.getTextForStep(step)
        }
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

    // The wizard does not talk to the pod. Each step that does is one command of the queue.

    @Test
    fun `pairing and priming the pod is a command of the queue`() {
        runStep(OmnipodWizardStep.INITIALIZE_POD)

        verifyBlocking(commandQueue) { customCommand(argThat { this is CommandInitializePod }) }
    }

    @Test
    fun `inserting the cannula is a command of the queue`() {
        runStep(OmnipodWizardStep.INSERT_CANNULA)

        verifyBlocking(commandQueue) { customCommand(argThat { this is CommandInsertCannula }) }
    }

    @Test
    fun `deactivating the pod is a command of the queue`() {
        runStep(OmnipodWizardStep.DEACTIVATE_POD)

        verifyBlocking(commandQueue) { customCommand(argThat { this is CommandDeactivatePod }) }
    }

    @Test
    fun `a step the queue could not run shows its reason`() {
        runBlocking { whenever(commandQueue.customCommand(argThat { this is CommandInitializePod })).thenReturn(enactResult) }
        whenever(enactResult.success).thenReturn(false)

        runStep(OmnipodWizardStep.INITIALIZE_POD)

        assertThat(sut.actionState.value).isEqualTo(ActionState.Error("error"))
    }

    /** Runs the wizard action of [step] the way the UI does, and waits until it has finished. */
    private fun runStep(step: OmnipodWizardStep) {
        sut.moveToStep(step)
        sut.executeAction()
        runBlocking {
            withTimeout(5_000) { sut.actionState.first { it !is ActionState.Executing } }
        }
    }
}
