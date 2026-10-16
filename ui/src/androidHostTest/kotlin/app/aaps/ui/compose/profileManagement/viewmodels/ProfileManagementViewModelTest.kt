package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.graph.profile.ProfileCompareData
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.singleBlock
import app.aaps.core.objects.extensions.singleTargetBlock
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.shared.tests.generatedTextResolver
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileManagementViewModelTest {

    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileFunction: ProfileFunction
    private val rh = generatedTextResolver()
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var nsClient: NsClient
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: ProfileManagementViewModel

    private val profilesFlow = MutableStateFlow<List<SingleProfile>>(emptyList())

    private fun profile(name: String) = SingleProfile(
        name = name,
        mgdl = true,
        ic = singleBlock(15.0),
        isf = singleBlock(100.0),
        basal = singleBlock(0.1),
        target = singleTargetBlock(110.0, 120.0)
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
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        // Pairing and reachability are inputs of uiState, so the combine needs real flows here.
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(true))
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        sut = ProfileManagementViewModel(
            profileRepository, profileFunction, rh, dateUtil, aapsLogger, activePlugin,
            profileUtil, decimalFormatter, persistenceLayer, insulinManager, preferences, config,
            nsClient, batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher())
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
    // Insulin choice for the activation screen
    // ---------------------------------------------------------------------------------------------

    private fun iCfg(label: String) = ICfg(insulinLabel = label, insulinEndTime = 360, insulinPeakTime = 75, concentration = 1.0)

    @Test
    fun `no picker is offered while an insulin is in force`() = runTest {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(iCfg("Running"))

        // The master resolves the in-force insulin itself, so the screen must not ask.
        assertThat(sut.insulinChoice().choices).isEmpty()
    }

    @Test
    fun `with nothing in force the catalogue is offered, preselected from the last switch`() = runTest {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)
        whenever(insulinManager.insulins).thenReturn(arrayListOf(iCfg("Novorapid"), iCfg("Fiasp")))
        val older = mock<PS>().also { whenever(it.timestamp).thenReturn(1_000L); whenever(it.iCfg).thenReturn(iCfg("Novorapid")) }
        val newest = mock<PS>().also { whenever(it.timestamp).thenReturn(2_000L); whenever(it.iCfg).thenReturn(iCfg("Fiasp")) }
        whenever(persistenceLayer.getProfileSwitches()).thenReturn(listOf(older, newest))

        val choice = sut.insulinChoice()
        assertThat(choice.choices).hasSize(2)
        // The most recent switch wins, not merely the last element of the list.
        assertThat(choice.preselected?.insulinLabel).isEqualTo("Fiasp")
    }

    @Test
    fun `preselection is empty when the last used insulin is no longer in the catalogue`() = runTest {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)
        whenever(insulinManager.insulins).thenReturn(arrayListOf(iCfg("Novorapid")))
        val deleted = mock<PS>().also { whenever(it.timestamp).thenReturn(1L); whenever(it.iCfg).thenReturn(iCfg("Deleted")) }
        whenever(persistenceLayer.getProfileSwitches()).thenReturn(listOf(deleted))

        // Nothing sensible to suggest → leave it unset so the user must choose; never substitute insulins[0].
        val choice = sut.insulinChoice()
        assertThat(choice.choices).hasSize(1)
        assertThat(choice.preselected).isNull()
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

    // --- The batch an activation submits (issue #5239: the picked time was dropped here) ---

    private fun actions(timestamp: Long, timeChanged: Boolean, withTT: Boolean = false, percentage: Int = 100, durationMinutes: Int = 0) =
        sut.buildActivationActions(
            profileName = "P0", durationMinutes = durationMinutes, percentage = percentage, timeshiftHours = 0,
            withTT = withTT, notes = "", timestamp = timestamp, timeChanged = timeChanged, iCfg = null
        )

    @Test
    fun `a picked time is carried in the profile switch action`() {
        val picked = 1_700_000_000_000L
        val ps = actions(timestamp = picked, timeChanged = true).filterIsInstance<BatchAction.ProfileSwitch>().single()
        assertThat(ps.timestamp).isEqualTo(picked)
    }

    @Test
    fun `an untouched time field leaves the stamping to the master`() {
        // 0 = now. The screen always hands over a timestamp, so only timeChanged tells a real pick apart
        // from the value the screen opened with.
        val ps = actions(timestamp = 1_700_000_000_000L, timeChanged = false).filterIsInstance<BatchAction.ProfileSwitch>().single()
        assertThat(ps.timestamp).isEqualTo(0L)
    }

    @Test
    fun `an activity temp target starts together with a switch put in the future`() {
        whenever(dateUtil.now()).thenReturn(1_700_000_000_000L)
        // ttTargetMgdl is an extension over this one preference; empty means "use the built-in default".
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("")
        val picked = 1_700_000_000_000L + T.mins(90).msecs()

        val tt = actions(timestamp = picked, timeChanged = true, withTT = true, percentage = 80, durationMinutes = 60)
            .filterIsInstance<BatchAction.TempTarget>().single()

        assertThat(tt.startOffsetMinutes).isEqualTo(90)
    }

    @Test
    fun `an activity temp target for a back-dated switch still starts now`() {
        whenever(dateUtil.now()).thenReturn(1_700_000_000_000L)
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("")
        val picked = 1_700_000_000_000L - T.mins(90).msecs()

        val tt = actions(timestamp = picked, timeChanged = true, withTT = true, percentage = 80, durationMinutes = 60)
            .filterIsInstance<BatchAction.TempTarget>().single()

        // A target in the past would be over before it is written, so the offset is never negative.
        assertThat(tt.startOffsetMinutes).isEqualTo(0)
    }

    // --- #2454: the pump rounds the basal, so the running profile (EPS) differs from the PS it was made from ---

    private fun localProfile(basal: Double) = SingleProfile(
        name = "P0", mgdl = true, ic = singleBlock(15.0), isf = singleBlock(100.0), basal = singleBlock(basal), target = singleTargetBlock(110.0, 120.0)
    )

    private val activatedPs = PS(
        id = 140, timestamp = 1_000L, basalBlocks = singleBlock(0.42), isfBlocks = singleBlock(100.0), icBlocks = singleBlock(15.0),
        targetBlocks = singleTargetBlock(110.0, 120.0), glucoseUnit = GlucoseUnit.MGDL, profileName = "P0", timeshift = 0, percentage = 100,
        duration = 0, iCfg = iCfg("Fiasp")
    )

    private fun runningEps(basal: Double, fromPs: Long?) = EPS(
        id = 311, timestamp = 1_000L, basalBlocks = singleBlock(basal), isfBlocks = singleBlock(100.0), icBlocks = singleBlock(15.0),
        targetBlocks = singleTargetBlock(110.0, 120.0), glucoseUnit = GlucoseUnit.MGDL, originalProfileName = "P0", originalCustomizedName = "P0",
        originalTimeshift = 0, originalPercentage = 100, originalDuration = 0, originalEnd = 0, originalPsId = fromPs, iCfg = iCfg("Fiasp")
    )

    private fun carouselFor(local: SingleProfile, eps: EPS, ps: PS?): ProfileCompareData? {
        whenever(dateUtil.formatHHMM(any())).thenReturn("00:00")
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        return sut.computeSelectedProfileAndCompareData(listOf(local), 0, eps, "P0", ps).second
    }

    @Test
    fun `a basal rounded by the pump is shown next to the profile, not as an edit`() {
        val compare = carouselFor(localProfile(0.42), runningEps(0.40, fromPs = 140), activatedPs)

        assertThat(compare).isNotNull()
        assertThat(compare!!.baseName).isEqualTo("P0")
        assertThat(compare.effectiveName).isEqualTo("Running")
        assertThat(compare.basalRows.first().value1).isEqualTo(NumberFormat.DECIMAL_2.format(0.42))
        assertThat(compare.basalRows.first().value2).isEqualTo(NumberFormat.DECIMAL_2.format(0.40))
    }

    @Test
    fun `a profile edited after it was activated is still shown as changed`() {
        val compare = carouselFor(localProfile(0.50), runningEps(0.40, fromPs = 140), activatedPs)

        // The changed layout puts the running profile first.
        assertThat(compare!!.baseName).isEqualTo("Running")
        assertThat(compare.effectiveName).isEqualTo("P0")
    }

    @Test
    fun `a profile the pump gives exactly shows no comparison`() {
        assertThat(carouselFor(localProfile(0.42), runningEps(0.42, fromPs = 140), activatedPs)).isNull()
    }

    /** By time, not by id: on AAPSClient originalPsId is the master's id (here 999, which no local PS has). */
    @Test
    fun `the activated PS is the one active when the EPS was written`() = runTest {
        whenever(persistenceLayer.getProfileSwitchActiveAt(1_000L)).thenReturn(activatedPs)

        assertThat(sut.activatedPs(runningEps(0.40, fromPs = 999))).isEqualTo(activatedPs)
    }

    @Test
    fun `a PS of another profile is not taken as the activated one`() = runTest {
        whenever(persistenceLayer.getProfileSwitchActiveAt(1_000L)).thenReturn(activatedPs.copy(profileName = "P1"))
        assertThat(sut.activatedPs(runningEps(0.40, fromPs = 140))).isNull()

        whenever(persistenceLayer.getProfileSwitchActiveAt(1_000L)).thenReturn(activatedPs.copy(percentage = 90))
        assertThat(sut.activatedPs(runningEps(0.40, fromPs = 140))).isNull()
    }

    /** Without the PS (older records) the EPS is all there is to compare with, as before. */
    @Test
    fun `without the activated PS a rounded basal still looks like an edit`() {
        val compare = carouselFor(localProfile(0.42), runningEps(0.40, fromPs = null), null)

        assertThat(compare!!.baseName).isEqualTo("Running")
    }
}
