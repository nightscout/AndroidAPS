package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.model.EPS
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ScreenMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileManagementViewModelTest {

    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: ProfileManagementViewModel

    private val profilesFlow = MutableStateFlow<List<SingleProfile>>(emptyList())

    private fun singleBlock(value: Double): JSONArray =
        JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", value))

    private fun profile(name: String) = SingleProfile(
        name = name,
        mgdl = true,
        ic = singleBlock(15.0),
        isf = singleBlock(100.0),
        basal = singleBlock(0.1),
        targetLow = singleBlock(110.0),
        targetHigh = singleBlock(120.0)
    )

    /** Publish [count] profiles named P0..P(count-1) as the repository's current list. */
    private fun givenProfiles(count: Int) {
        profilesFlow.value = List(count) { profile("P$it") }
    }

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} observers' launchIn and the stateIn collector
        // (no advanceUntilIdle), so uiState stays at its default value and we test the synchronous
        // methods. The cold-flow chains that init builds synchronously must still be non-null:
        //  - profileRepository.profiles is read via .value.map{} in observeNewProfileSelection()
        //  - persistenceLayer.observeChanges(EPS) is built in observeActiveProfileForAutoNavigation()
        //    and in the uiState combine.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(profileRepository.profiles).thenReturn(profilesFlow)
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        sut = ProfileManagementViewModel(
            profileRepository, profileFunction, rh, dateUtil, aapsLogger, activePlugin,
            profileUtil, decimalFormatter, persistenceLayer, preferences, config,
            batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is loading, in EDIT mode and empty`() {
        val state = sut.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.screenMode).isEqualTo(ScreenMode.EDIT)
        assertThat(state.profileNames).isEmpty()
        assertThat(state.currentProfileIndex).isEqualTo(0)
        assertThat(state.activeProfileSwitch).isNull()
    }

    @Test
    fun `getReuseValues returns null when there is no active profile switch`() {
        assertThat(sut.getReuseValues()).isNull()
    }

    @Test
    fun `isPumpCompatible returns true when there is no profile at the index`() {
        assertThat(sut.isPumpCompatible(0, 100)).isTrue()
    }

    // ---------------------------------------------------------------------------------------------
    // Reorder ("sort") mode
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `reorderOrder is null until reorder mode is entered`() {
        assertThat(sut.reorderOrder.value).isNull()
    }

    @Test
    fun `entering reorder mode starts from the identity order`() {
        givenProfiles(4)
        sut.enterReorderMode()
        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `entering reorder mode with no profiles is a no-op`() {
        sut.enterReorderMode()
        assertThat(sut.reorderOrder.value).isNull()
    }

    @Test
    fun `a non-adjacent move is applied as remove-then-insert, not a swap`() {
        givenProfiles(4)
        sut.enterReorderMode()
        // CarouselReorderConfig.onMove is declared for arbitrary index pairs, so a multi-slot move
        // must shift the items in between. A swap would give [3, 1, 2, 0].
        assertThat(sut.moveReorderItem(0, 3)).isTrue()
        assertThat(sut.reorderOrder.value).containsExactly(1, 2, 3, 0).inOrder()
    }

    @Test
    fun `moves that cannot apply are rejected so the carousel does not follow them`() {
        givenProfiles(3)
        sut.enterReorderMode()
        // The carousel scrolls to the target only when the move reports success, so a rejected move
        // must return false rather than silently doing nothing.
        assertThat(sut.moveReorderItem(1, 1)).isFalse()
        assertThat(sut.moveReorderItem(-1, 2)).isFalse()
        assertThat(sut.moveReorderItem(0, 7)).isFalse()
        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `a move outside reorder mode is rejected`() {
        givenProfiles(3)
        assertThat(sut.moveReorderItem(0, 1)).isFalse()
    }

    @Test
    fun `cancelling leaves the repository untouched`() = runTest {
        givenProfiles(3)
        sut.enterReorderMode()
        sut.moveReorderItem(0, 2)
        sut.cancelReorder()
        assertThat(sut.reorderOrder.value).isNull()
        verify(profileRepository, never()).reorder(any())
    }

    @Test
    fun `committing an unchanged order never touches the repository`() = runTest {
        givenProfiles(3)
        sut.enterReorderMode()
        val settledOn = sut.commitReorder()
        assertThat(settledOn).isEqualTo(0)
        assertThat(sut.reorderOrder.value).isNull()
        // Skipping the write is what makes leaving sort mode free: a persist would bump the
        // profile-store timestamp and provoke a full Nightscout upload.
        verify(profileRepository, never()).reorder(any())
    }

    @Test
    fun `committing persists the working order exactly once`() = runTest {
        givenProfiles(4)
        whenever(profileRepository.reorder(any())).thenReturn(Result.success(Unit))
        sut.enterReorderMode()
        sut.moveReorderItem(0, 2)
        sut.moveReorderItem(3, 0)

        sut.commitReorder()

        verify(profileRepository, times(1)).reorder(listOf(3, 1, 2, 0))
    }

    @Test
    fun `committing settles on the profile that was moved`() = runTest {
        givenProfiles(4)
        whenever(profileRepository.reorder(any())).thenReturn(Result.success(Unit))
        sut.selectProfile(3)
        sut.enterReorderMode()
        // Move the profile originally at index 0 into position 2.
        sut.moveReorderItem(0, 2)

        // Selection is positional, so it has to follow the moved profile through the permutation —
        // otherwise the screen silently ends up showing a different profile.
        assertThat(sut.commitReorder()).isEqualTo(2)
    }

    @Test
    fun `committing without moving anything settles on the profile that was selected`() = runTest {
        givenProfiles(4)
        sut.selectProfile(2)
        sut.enterReorderMode()
        assertThat(sut.commitReorder()).isEqualTo(2)
    }

    @Test
    fun `an undone move settles on the same card a committed move would`() = runTest {
        givenProfiles(4)
        sut.selectProfile(1)
        sut.enterReorderMode()
        // Move profile 2 later then back again: the net order is the identity, but the card the user
        // was working on is still profile 2 — not the one that happened to be selected on entry.
        sut.moveReorderItem(2, 3)
        sut.moveReorderItem(3, 2)

        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(sut.commitReorder()).isEqualTo(2)
        verify(profileRepository, never()).reorder(any())
    }

    @Test
    fun `a failed commit reports it and leaves reorder mode`() = runTest {
        givenProfiles(3)
        whenever(rh.gs(any<Int>())).thenReturn("message")
        whenever(profileRepository.reorder(any()))
            .thenReturn(Result.failure(IllegalArgumentException("list replaced mid-reorder")))
        sut.enterReorderMode()
        sut.moveReorderItem(0, 2)

        assertThat(sut.commitReorder()).isNull()
        assertThat(sut.reorderOrder.value).isNull()
    }

    @Test
    fun `a same-size replacement of the profile list aborts the commit`() = runTest {
        givenProfiles(3)
        whenever(rh.gs(any<Int>())).thenReturn("message")
        sut.enterReorderMode()
        sut.moveReorderItem(0, 2)

        // An NS push swaps in a different profile set of the same length. The working order is a list
        // of indices, so it would pass the repository's permutation check and silently rearrange
        // profiles the user never saw.
        profilesFlow.value = listOf(profile("X"), profile("Y"), profile("Z"))

        assertThat(sut.commitReorder()).isNull()
        assertThat(sut.reorderOrder.value).isNull()
        verify(profileRepository, never()).reorder(any())
    }

    @Test
    fun `committing outside reorder mode does nothing`() = runTest {
        givenProfiles(3)
        assertThat(sut.commitReorder()).isNull()
        verify(profileRepository, never()).reorder(any())
    }
}
