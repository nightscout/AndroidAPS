package app.aaps.pump.eopatch.compose

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import com.google.common.truth.Truth.assertThat
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
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit test for [EopatchPatchViewModel]'s synchronous, pure state mutators against the default UI
 * state. The VM's init{} kicks off a `viewModelScope.launch { loadSiteRotationEntries() }` (which
 * would touch the unstubbed persistenceLayer) and two RxJava subscriptions; using a
 * StandardTestDispatcher for Dispatchers.Main DEFERS the coroutine so construction stays clean, while
 * the rx streams are stubbed to empty and observed on a trampoline scheduler. We only assert the
 * deterministic outcomes of the pure select/update setters and the initial StateFlow seeds — the
 * BLE / navigation / alarm flows are RxJava-heavy and out of scope here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EopatchPatchViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var patchManager: IPatchManager
    @Mock private lateinit var preferenceManager: PreferenceManager
    @Mock private lateinit var alarmRegistry: IAlarmRegistry
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var persistenceLayer: PersistenceLayer

    // Concrete final collaborators (Mockito 5 inline handles final classes).
    private val patchManagerExecutor: PatchManagerExecutor = mock()
    private val patchConfig: PatchConfig = mock()
    private val rxAction: RxAction = mock()

    // Default zero-state patch state; stored in a non-null val at construction.
    private val patchState = PatchState()

    private lateinit var sut: EopatchPatchViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched loadSiteRotationEntries() coroutine
        // (no advanceUntilIdle), so construction stays clean and we test the synchronous setters.
        Dispatchers.setMain(StandardTestDispatcher())

        // Field-initializer reads a non-null PatchState from the preference manager.
        whenever(preferenceManager.patchState).thenReturn(patchState)
        // init{} builds an rx subscription (B012 update) and observes lifecycle on aapsSchedulers.main.
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(preferenceManager.observePatchLifeCycle()).thenReturn(Observable.empty())

        sut = EopatchPatchViewModel(
            rh, patchManager, patchManagerExecutor, preferenceManager, patchConfig, alarmRegistry,
            aapsLogger, aapsSchedulers, rxAction, preferences, insulinManager, profileFunction,
            profileRepository, persistenceLayer
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default state seeds are at their initial values`() {
        assertThat(sut.patchStep.value).isNull()
        assertThat(sut.setupStep.value).isNull()
        assertThat(sut.title.value).isEqualTo(R.string.string_activate_patch)
        assertThat(sut.safetyCheckProgress.value).isEqualTo(0)
        assertThat(sut.isActivated.value).isFalse()
        assertThat(sut.availableInsulins.value).isEmpty()
        assertThat(sut.selectedInsulin.value).isNull()
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
        assertThat(sut.selectedProfile.value).isNull()
    }

    @Test
    fun `step progress on the default (null) step is the full activation flow`() {
        // Null patchStep -> else branches: 9 total steps, index 0.
        assertThat(sut.totalSteps).isEqualTo(9)
        assertThat(sut.currentStepIndex).isEqualTo(0)
    }

    @Test
    fun `selectInsulin publishes the chosen insulin`() {
        val iCfg: ICfg = mock()

        sut.selectInsulin(iCfg)

        assertThat(sut.selectedInsulin.value).isSameInstanceAs(iCfg)
    }

    @Test
    fun `selectProfile publishes the chosen profile name`() {
        sut.selectProfile("Morning")

        assertThat(sut.selectedProfile.value).isEqualTo("Morning")
    }

    @Test
    fun `updateSiteLocation and updateSiteArrow publish the chosen values`() {
        sut.updateSiteLocation(TE.Location.SIDE_LEFT_UPPER_ARM)
        sut.updateSiteArrow(TE.Arrow.UP)

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.SIDE_LEFT_UPPER_ARM)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.UP)
    }
}
