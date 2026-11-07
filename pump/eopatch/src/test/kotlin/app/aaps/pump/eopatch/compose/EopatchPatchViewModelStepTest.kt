package app.aaps.pump.eopatch.compose

import app.aaps.core.data.model.TE
import app.aaps.core.keys.BooleanKey
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers [EopatchPatchViewModel.moveStep], the step machine behind the patch activation wizard.
 *
 * Moving between steps is not only navigation - most steps also record where the patch is in its
 * life cycle, and that record is what the driver believes about a patch stuck to the user. The two
 * branches that matter are re-entering the step you are already on, which must not rewind anything,
 * and cancelling, which may only shut a patch down when it was never activated.
 */
class EopatchPatchViewModelStepTest : EopatchViewModelTestBase() {

    @BeforeEach
    fun setUp() {
        setUpMocks()
    }

    @AfterEach
    fun tearDown() {
        tearDownMocks()
    }

    @Test
    fun movingToAStepRecordsThatStepInTheLifeCycle() {
        val viewModel = sut()

        viewModel.moveStep(PatchStep.REMOVE_NEEDLE_CAP)

        verify(preferenceManager).updatePatchLifeCycle(any<PatchLifecycleEvent>())
    }

    /** Re-entering the step already showing must not write the life cycle again. */
    @Test
    fun movingToTheStepAlreadyShowingRecordsNothingNew() {
        val viewModel = sut()
        viewModel.moveStep(PatchStep.REMOVE_NEEDLE_CAP)

        viewModel.moveStep(PatchStep.REMOVE_NEEDLE_CAP)

        // Once for the first move, not twice.
        verify(preferenceManager).updatePatchLifeCycle(any<PatchLifecycleEvent>())
    }

    @Test
    fun theStepMovesEvenWhenNothingIsRecorded() {
        val viewModel = sut()

        viewModel.moveStep(PatchStep.SITE_LOCATION)

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.SITE_LOCATION)
    }

    /** Cancelling before the patch was ever activated shuts the half-built one down. */
    @Test
    fun cancellingAnUnactivatedPatchShutsItDown() {
        whenever(patchConfig.isActivated).thenReturn(false)
        val viewModel = sut()

        viewModel.moveStep(PatchStep.CANCEL)

        verify(preferenceManager).updatePatchLifeCycle(any<PatchLifecycleEvent>())
    }

    /**
     * Cancelling out of the wizard when a patch is already activated must leave it alone. Shutting
     * it down here would stop insulin from a patch the user is still wearing.
     */
    @Test
    fun cancellingAnActivatedPatchLeavesItRunning() {
        whenever(patchConfig.isActivated).thenReturn(true)
        val viewModel = sut()

        viewModel.moveStep(PatchStep.CANCEL)

        verify(preferenceManager, never()).updatePatchLifeCycle(any<PatchLifecycleEvent>())
    }

    /** Going back to the start clears the flag left by a failed needle sensing. */
    @Test
    fun goingBackToWakeUpClearsTheNeedleSensingError() {
        val viewModel = sut()

        viewModel.moveStep(PatchStep.WAKE_UP)

        verify(patchConfig).rotateKnobNeedleSensingError = false
    }

    // ---- onConfirm: where the Next button leads from each step ----

    private fun viewModelAtStep(step: PatchStep) = sut().also { it.moveStep(step) }

    /** Discarding in order to change the patch leads back to the start of a new activation. */
    @Test
    fun confirmingAfterADiscardForChangeStartsANewPatch() {
        val viewModel = viewModelAtStep(PatchStep.DISCARDED_FOR_CHANGE)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.WAKE_UP)
    }

    /** Discarding because an alarm demanded it just ends the wizard. */
    @Test
    fun confirmingAfterADiscardFromAnAlarmFinishes() {
        val viewModel = viewModelAtStep(PatchStep.DISCARDED_FROM_ALARM)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.FINISH)
    }

    @Test
    fun confirmingAPlainDiscardGoesBackHome() {
        val viewModel = viewModelAtStep(PatchStep.DISCARDED)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.BACK_TO_HOME)
    }

    /** Not started as a safe deactivation, so turning the alarm off leads to a plain discard. */
    @Test
    fun confirmingTheAlarmStepDiscardsThePatch() {
        val viewModel = viewModelAtStep(PatchStep.MANUALLY_TURNING_OFF_ALARM)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.DISCARDED)
    }

    @Test
    fun confirmingTheBasalScheduleCompletesWhenThePatchIsConnected() {
        whenever(patchManagerExecutor.patchConnectionState).thenReturn(BleConnectionState.CONNECTED)
        val viewModel = viewModelAtStep(PatchStep.BASAL_SCHEDULE)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.COMPLETE)
    }

    /**
     * With no connection the wizard must not declare the patch done - the basal schedule has not
     * reached it yet. It checks the connection instead.
     */
    @Test
    fun confirmingTheBasalScheduleDoesNotCompleteWhileDisconnected() {
        whenever(patchManagerExecutor.patchConnectionState).thenReturn(BleConnectionState.DISCONNECTED)
        val viewModel = viewModelAtStep(PatchStep.BASAL_SCHEDULE)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isNotEqualTo(PatchStep.COMPLETE)
    }

    /** A step with nothing to confirm must stay put rather than fall through to somewhere else. */
    @Test
    fun confirmingAStepWithNoNextLeavesTheWizardWhereItIs() {
        val viewModel = viewModelAtStep(PatchStep.SAFETY_CHECK)

        viewModel.onConfirm()

        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.SAFETY_CHECK)
    }

    // ---- the site location step is optional ----

    @Test
    fun theSiteLocationStepFollowsThePreference() {
        whenever(preferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(true)
        assertThat(sut().showSiteLocationStep).isTrue()

        whenever(preferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(false)
        assertThat(sut().showSiteLocationStep).isFalse()
    }

    @Test
    fun aFreshWizardHasNotChosenASiteYet() {
        val viewModel = sut()

        assertThat(viewModel.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(viewModel.siteArrow.value).isEqualTo(TE.Arrow.NONE)
    }
}
