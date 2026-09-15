package app.aaps.pump.eopatch.compose

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
@OptIn(ExperimentalCoroutinesApi::class)
class EopatchPatchViewModelStepTest {

    private val rh: ResourceHelper = mock()
    private val patchManager: IPatchManager = mock()
    private val patchManagerExecutor: PatchManagerExecutor = mock()
    private val preferenceManager: PreferenceManager = mock()
    private val patchConfig: PatchConfig = mock()
    private val alarmRegistry: IAlarmRegistry = mock()
    private val aapsLogger: AAPSLogger = mock()
    private val aapsSchedulers: AapsSchedulers = mock()
    private val rxAction: RxAction = mock()
    private val preferences: Preferences = mock()
    private val insulinManager: InsulinManager = mock()
    private val profileFunction: ProfileFunction = mock()
    private val profileRepository: ProfileRepository = mock()
    private val persistenceLayer: PersistenceLayer = mock()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        // viewModelScope runs on Main, which a unit test has to provide.
        Dispatchers.setMain(testDispatcher)
        // What the view model's init block touches.
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(preferenceManager.observePatchLifeCycle()).thenReturn(Observable.never())
        whenever(preferenceManager.patchState).thenReturn(PatchState())
        // Alarm handling rides on Maybe; a bare mock hands back null and the chain dies on it.
        whenever(alarmRegistry.remove(any())).thenReturn(Maybe.empty())
        // add() has a third parameter with a default, so all three have to be matched.
        whenever(alarmRegistry.add(any(), any(), any())).thenReturn(Maybe.empty())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sut() = EopatchPatchViewModel(
        rh, patchManager, patchManagerExecutor, preferenceManager, patchConfig, alarmRegistry,
        aapsLogger, aapsSchedulers, rxAction, preferences, insulinManager, profileFunction,
        profileRepository, persistenceLayer
    )

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
