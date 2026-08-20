package app.aaps.plugins.aps.autotune.compose

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.autotune.AutotuneFS
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.elements.WeekDay
import app.aaps.plugins.aps.autotune.events.EventAutotuneUpdateGui
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Unit test for the SHARED, synchronous state logic of [AutotuneViewModel].
 *
 * The VM injects its own [CoroutineScope]. Backing it with a [StandardTestDispatcher] (never
 * advanced) DEFERS the heavy `init { ... scope.launch { refreshState() } }` and the rxBus flow
 * collection, so construction stays clean and we can exercise the pure setter/toggle methods against
 * the default [AutotuneUiState]. Only the synchronous slice of `init` runs at construction
 * (`checkNewDay()` -> `resetParam(false)`); it is kept off the `days` path by forcing `runToday`
 * true via a large `lastRun`. The [AutotunePlugin] concrete final class is mocked with the inline
 * mock maker (same pattern as DanaPump in DanaOverviewViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AutotuneViewModelTest {

    @Mock private lateinit var autotuneFS: AutotuneFS
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var loop: Loop

    private val autotunePlugin: AutotunePlugin = mock()
    private val profileStoreProvider: Provider<ProfileStore> = mock()
    private val atProfileProvider: Provider<ATProfile> = mock()

    private lateinit var sut: AutotuneViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        // Non-null members dereferenced synchronously in init { }:
        whenever(autotunePlugin.lastNbDays).thenReturn("5")   // non-empty -> skips the default-days assignment
        whenever(autotunePlugin.result).thenReturn("")        // .isEmpty() in checkNewDay()
        whenever(autotunePlugin.lastRun).thenReturn(Long.MAX_VALUE) // forces runToday true -> resetParam(false), off the days path
        whenever(rxBus.toFlow(EventAutotuneUpdateGui::class)).thenReturn(emptyFlow())

        // StandardTestDispatcher-backed scope: scope.launch { refreshState() } and the rxBus flow
        // collection stay queued (never advanced), so the default uiState is what we assert against.
        // Its own scheduler, not the one Dispatchers.Main hands out, so a test that advances time to drive
        // some other VM does not also release this one's init into its unstubbed dependencies.
        sut = AutotuneViewModel(
            autotunePlugin, autotuneFS, profileFunction, profileUtil, profileRepository, preferences,
            dateUtil, rh, rxBus, uel, loop, profileStoreProvider, atProfileProvider,
            CoroutineScope(StandardTestDispatcher(TestCoroutineScheduler()))
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state before any deferred refresh`() {
        val state = sut.uiState.value

        assertThat(state.profileList).isEmpty()
        assertThat(state.selectedProfileIndex).isEqualTo(0)
        assertThat(state.daysBack).isEqualTo(5.0)
        assertThat(state.showWeekDays).isFalse()
        assertThat(state.isRunning).isFalse()
        assertThat(state.dialogState).isEqualTo(DialogState.None)
    }

    @Test
    fun `onToggleWeekDays flips showWeekDays`() {
        assertThat(sut.uiState.value.showWeekDays).isFalse()

        sut.onToggleWeekDays()
        assertThat(sut.uiState.value.showWeekDays).isTrue()

        sut.onToggleWeekDays()
        assertThat(sut.uiState.value.showWeekDays).isFalse()
    }

    @Test
    fun `dismissDialog leaves dialogState as None`() {
        sut.dismissDialog()

        assertThat(sut.uiState.value.dialogState).isEqualTo(DialogState.None)
    }

    /**
     * A VM on a scheduler this test can advance, so `onProfileSwitchConfirm()`'s `scope.launch` actually runs.
     * Advancing also releases the queued `init { scope.launch { refreshState() } }`; the SupervisorJob keeps that
     * unrelated coroutine from cancelling the one under test if its own (heavily mocked) dependencies give out.
     */
    private fun advanceableSut(scheduler: TestCoroutineScheduler): AutotuneViewModel {
        val profileStore = mock<ProfileStore>()
        whenever(profileStore.getProfileList()).thenReturn(ArrayList())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(profileStore))
        whenever(autotunePlugin.selectedProfile).thenReturn("")
        whenever(autotunePlugin.days).thenReturn(WeekDay())
        whenever(autotunePlugin.calcDays(any())).thenReturn(1)
        whenever(rh.gs(app.aaps.core.ui.R.string.active)).thenReturn("Active")
        whenever(rh.gs(app.aaps.core.ui.R.string.profileswitch_ismissing)).thenReturn("No profile")
        whenever(dateUtil.dateAndTimeString(any())).thenReturn("now")
        return AutotuneViewModel(
            autotunePlugin, autotuneFS, profileFunction, profileUtil, profileRepository, preferences,
            dateUtil, rh, rxBus, uel, loop, profileStoreProvider, atProfileProvider,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        )
    }

    // Confirming the switch has to record an insulin. Autotune never tunes the insulin itself, so with nothing in
    // force there is nothing to carry over — the user is told instead of having one substituted.
    @Test
    fun `confirming the profile switch with no insulin in force reports instead of switching`() = runTest {
        val tunedProfile = mock<ATProfile>()
        whenever(tunedProfile.profileName).thenReturn("Tuned")
        whenever(tunedProfile.profileStore(false)).thenReturn(mock())
        whenever(autotunePlugin.tunedProfile).thenReturn(tunedProfile)
        whenever(preferences.get(BooleanKey.AutotuneCircadianIcIsf)).thenReturn(false)
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)
        whenever(rh.gs(app.aaps.core.ui.R.string.autotune)).thenReturn("Autotune")
        whenever(rh.gs(app.aaps.core.ui.R.string.profile_switch_no_insulin)).thenReturn("No insulin in use")

        advanceableSut(testScheduler).onProfileSwitchConfirm()
        advanceUntilIdle()

        val captor = argumentCaptor<EventShowDialog.Ok>()
        verify(rxBus).send(captor.capture())
        assertThat(captor.firstValue.message).isEqualTo("No insulin in use")
        verifyBlocking(profileFunction, never()) {
            createProfileSwitch(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }
    }
}
