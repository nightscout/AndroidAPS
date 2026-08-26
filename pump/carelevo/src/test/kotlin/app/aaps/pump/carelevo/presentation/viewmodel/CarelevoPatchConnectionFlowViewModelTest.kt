package app.aaps.pump.carelevo.presentation.viewmodel

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Optional

/**
 * Unit tests for [CarelevoPatchConnectionFlowViewModel] — the activation wizard's step machine.
 *
 * **Why Robolectric and not a plain JVM/JUnit5 test:** every collaborator of this ViewModel is a
 * mockable interface and construction touches none of them, so a JVM test would cover almost all of
 * it. The single exception is [CarelevoPatchConnectionFlowViewModel.bodyType], which resolves
 * [BodyType.fromPref]. Touching [BodyType] initialises its enum constants eagerly, and those embed
 * `List<Pair<TE.Location, android.graphics.Path>>` zone tables — on a bare JVM `android.graphics.Path`
 * is the "Stub!" class and enum class-init throws. Under [RobolectricTestRunner] `Path` is shadowed
 * for real, so `bodyType()` is exercised like every other public member instead of being the one hole
 * in the suite. No Android [android.content.Context] is needed otherwise.
 *
 * **Coroutines:** `viewModelScope` dispatches on `Dispatchers.Main`, so [testDispatcher] — an
 * [UnconfinedTestDispatcher] — is installed as Main. Unconfined runs the VM's `viewModelScope.launch`
 * bodies eagerly and in-line (the mocked `suspend` collaborators never really suspend), so a state
 * transition has already settled by the time the call under test returns. Every test passes that same
 * dispatcher to `runTest`, which makes the TestScope, the Main dispatcher and the event collector
 * share one scheduler; `advanceUntilIdle()` is kept as a defensive flush.
 *
 * **Rx:** [AapsSchedulers.io] is stubbed to [Schedulers.trampoline] so the force-discard chain runs
 * synchronously on the test thread. That chain's terminal `subscribe { }` has no `onError` arm, so an
 * erroring source routes an `OnErrorNotImplementedException` to [RxJavaPlugins]; the global error
 * handler is neutralised in [setUp] so the `doOnError` branch can be asserted without polluting the run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoPatchConnectionFlowViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var aapsSchedulers: AapsSchedulers
    private lateinit var carelevoPatch: CarelevoPatch
    private lateinit var commandQueue: CommandQueue
    private lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
    private lateinit var preferences: Preferences
    private lateinit var profileFunction: ProfileFunction
    private lateinit var profileRepository: ProfileRepository
    private lateinit var insulinManager: InsulinManager
    private lateinit var persistenceLayer: PersistenceLayer

    private lateinit var patchStateSubject: BehaviorSubject<Optional<PatchState>>

    private lateinit var sut: CarelevoPatchConnectionFlowViewModel

    private val fiasp = ICfg(insulinLabel = "Fiasp", peak = 55, dia = 6.0, concentration = 1.0)
    private val lyumjev = ICfg(insulinLabel = "Lyumjev", peak = 45, dia = 5.0, concentration = 1.0)

    /** The step list the ViewModel starts with, before `initWorkflow` builds the real one. */
    private val defaultSteps = listOf(
        CarelevoPatchStep.PATCH_START,
        CarelevoPatchStep.PATCH_CONNECT,
        CarelevoPatchStep.SAFETY_CHECK,
        CarelevoPatchStep.PATCH_ATTACH,
        CarelevoPatchStep.NEEDLE_INSERTION
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The force-discard chain ends in subscribe(onSuccess) with no onError arm; swallow the
        // resulting undeliverable OnErrorNotImplementedException so the doOnError test stays quiet.
        RxJavaPlugins.setErrorHandler { }

        aapsLogger = mock()
        aapsSchedulers = mock()
        carelevoPatch = mock()
        commandQueue = mock()
        patchForceDiscardUseCase = mock()
        preferences = mock()
        profileFunction = mock()
        profileRepository = mock()
        insulinManager = mock()
        persistenceLayer = mock()

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())

        patchStateSubject = BehaviorSubject.create()
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject)

        // Construction reads no collaborator — every field is a plain initialiser.
        sut = CarelevoPatchConnectionFlowViewModel(
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            patchForceDiscardUseCase = patchForceDiscardUseCase,
            preferences = preferences,
            profileFunction = profileFunction,
            profileRepository = profileRepository,
            insulinManager = insulinManager,
            persistenceLayer = persistenceLayer
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        RxJavaPlugins.reset()
    }

    // ---- helpers ------------------------------------------------------------------------------

    /**
     * Subscribe to the VM's one-shot event flow *before* the action under test runs. The collector is
     * launched on an [UnconfinedTestDispatcher] sharing the TestScope scheduler, so it registers on the
     * underlying SharedFlow synchronously — an `EventFlow` slot is consumable exactly once, so
     * subscribing first is what makes the emission observable.
     */
    private fun TestScope.collectEvents(): MutableList<Event> {
        val events = mutableListOf<Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sut.event.collect { events.add(it) }
        }
        return events
    }

    /** Stub the collaborators `initWorkflow` fans out to. `suspend` stubs need a coroutine body. */
    private suspend fun stubWorkflow(
        needsProfileGate: Boolean = false,
        siteRotation: Boolean = false,
        insulins: List<ICfg> = listOf(fiasp),
        activeLabel: String? = "Fiasp"
    ) {
        val requested: PS? = if (needsProfileGate) null else mock<PS>()
        whenever(profileFunction.getRequestedProfile()).thenReturn(requested)
        whenever(preferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(siteRotation)
        whenever(insulinManager.insulins).thenReturn(ArrayList(insulins))
        val effective: EffectiveProfile? =
            activeLabel?.let { label -> mock { on { iCfg } doReturn ICfg(label, 55, 6.0, 1.0) } }
        whenever(profileFunction.getProfile()).thenReturn(effective)
    }

    private suspend fun stubProfileList(names: List<String>, originalName: String) {
        val profiles = names.map { profileName -> mock<SingleProfile> { on { name } doReturn profileName } }
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(profiles))
        whenever(profileFunction.getOriginalProfileName()).thenReturn(originalName)
    }

    private fun te(eventType: TE.Type): TE = mock { on { type } doReturn eventType }

    /**
     * Build + stub the enact result eagerly. It must be fully stubbed *before* it is handed to the
     * `customCommand` stub — nesting a mock definition inside an in-flight `whenever()` trips
     * `UnfinishedStubbingException`.
     */
    private fun enactResult(success: Boolean): PumpEnactResult {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        return result
    }

    private fun connectedPatch() = patchStateSubject.onNext(Optional.of<PatchState>(PatchState.ConnectedBooted))

    /** All-matcher stub for the 11-arg `createProfileSwitch` overload the profile gate uses. */
    private suspend fun stubCreateProfileSwitch(result: PS?) {
        whenever(
            profileFunction.createProfileSwitch(
                any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any()
            )
        ).thenReturn(result)
    }

    private fun verifyNoProfileSwitchCreated() {
        verifyBlocking(profileFunction, never()) {
            createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())
        }
    }

    // ---- defaults -----------------------------------------------------------------------------

    @Test
    fun `initial state exposes the default step list at the first step`() {
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.totalSteps.value).isEqualTo(defaultSteps.size)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(sut.inputInsulin).isEqualTo(300)
        assertThat(sut.isCreated).isFalse()
    }

    @Test
    fun `initial host state is empty`() {
        assertThat(sut.availableProfiles.value).isEmpty()
        assertThat(sut.selectedProfile.value).isNull()
        assertThat(sut.availableInsulins.value).isEmpty()
        assertThat(sut.selectedInsulin.value).isNull()
        assertThat(sut.activeInsulinLabel.value).isNull()
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
        assertThat(sut.siteRotationEntries()).isEmpty()
        assertThat(sut.showInsulinStep).isFalse()
    }

    @Test
    fun `setIsCreated flips the created latch both ways`() {
        sut.setIsCreated(true)
        assertThat(sut.isCreated).isTrue()

        sut.setIsCreated(false)
        assertThat(sut.isCreated).isFalse()
    }

    @Test
    fun `concentrationEnabled reads the insulin concentration preference`() {
        whenever(preferences.get(BooleanKey.GeneralInsulinConcentration)).thenReturn(true)
        assertThat(sut.concentrationEnabled).isTrue()

        whenever(preferences.get(BooleanKey.GeneralInsulinConcentration)).thenReturn(false)
        assertThat(sut.concentrationEnabled).isFalse()
    }

    // ---- setPage / ordinal progress -----------------------------------------------------------

    @Test
    fun `setPage moves the page and reports the step ordinal within the workflow`() {
        sut.setPage(CarelevoPatchStep.SAFETY_CHECK)

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.SAFETY_CHECK)
        assertThat(sut.currentStepIndex.value).isEqualTo(defaultSteps.indexOf(CarelevoPatchStep.SAFETY_CHECK))
    }

    @Test
    fun `setPage to a step outside this run's workflow clamps the ordinal to zero`() {
        sut.setPage(CarelevoPatchStep.NEEDLE_INSERTION)
        assertThat(sut.currentStepIndex.value).isEqualTo(4)

        // PROFILE_GATE is not part of the default (pre-initWorkflow) list -> indexOf == -1 -> coerced.
        sut.setPage(CarelevoPatchStep.PROFILE_GATE)

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PROFILE_GATE)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
    }

    @Test
    fun `setInputInsulin stores the fill amount`() {
        sut.setInputInsulin(180)

        assertThat(sut.inputInsulin).isEqualTo(180)
    }

    // ---- initWorkflow -------------------------------------------------------------------------

    @Test
    fun `initWorkflow without gate, site rotation or insulin choice builds the minimal step list`() = runTest(testDispatcher) {
        stubWorkflow(needsProfileGate = false, siteRotation = false, insulins = listOf(fiasp))

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.totalSteps.value).isEqualTo(6)
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.showInsulinStep).isFalse()
        assertThat(sut.availableProfiles.value).isEmpty()
        // Site placement is reset every run so a previous run's choice cannot leak into this one.
        verify(carelevoPatch).setSitePlacement(TE.Location.NONE, TE.Arrow.NONE)
        // Site-rotation history is only loaded when the step is actually part of the run.
        verifyBlocking(persistenceLayer, never()) { getTherapyEventDataFromTime(any(), any<Boolean>()) }
    }

    @Test
    fun `initWorkflow adds the profile gate, insulin choice and site location when all are needed`() = runTest(testDispatcher) {
        stubWorkflow(needsProfileGate = true, siteRotation = true, insulins = listOf(fiasp, lyumjev))
        stubProfileList(names = listOf("Adult", "Night"), originalName = "Night")
        val history = listOf(te(TE.Type.CANNULA_CHANGE), te(TE.Type.SENSOR_CHANGE), te(TE.Type.INSULIN_CHANGE))
        whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<Boolean>())).thenReturn(history)

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.totalSteps.value).isEqualTo(9)
        assertThat(sut.showInsulinStep).isTrue()
        // First entry of the built list -> the gate.
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PROFILE_GATE)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.availableProfiles.value).containsExactly("Adult", "Night").inOrder()
        assertThat(sut.selectedProfile.value).isEqualTo("Night")
        // Only cannula/sensor changes are site-rotation relevant; the insulin change is dropped.
        assertThat(sut.siteRotationEntries()).hasSize(2)
    }

    @Test
    fun `initWorkflow falls back to the first profile when the active one is not in the list`() = runTest(testDispatcher) {
        stubWorkflow(needsProfileGate = true)
        stubProfileList(names = listOf("Adult", "Night"), originalName = "Deleted")

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.selectedProfile.value).isEqualTo("Adult")
    }

    @Test
    fun `initWorkflow selects the insulin matching the active profile and records its label`() = runTest(testDispatcher) {
        stubWorkflow(insulins = listOf(fiasp, lyumjev), activeLabel = "Lyumjev")

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.availableInsulins.value.map { it.insulinLabel }).containsExactly("Fiasp", "Lyumjev").inOrder()
        assertThat(sut.selectedInsulin.value?.insulinLabel).isEqualTo("Lyumjev")
        assertThat(sut.activeInsulinLabel.value).isEqualTo("Lyumjev")
    }

    @Test
    fun `initWorkflow falls back to the first insulin when no profile is active`() = runTest(testDispatcher) {
        stubWorkflow(insulins = listOf(fiasp, lyumjev), activeLabel = null)

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.selectedInsulin.value?.insulinLabel).isEqualTo("Fiasp")
        assertThat(sut.activeInsulinLabel.value).isNull()
    }

    @Test
    fun `initWorkflow clones the insulins so editing the selection cannot corrupt InsulinManager`() = runTest(testDispatcher) {
        stubWorkflow(insulins = listOf(fiasp))

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        val exposed = sut.availableInsulins.value.single()
        assertThat(exposed).isEqualTo(fiasp)
        assertThat(exposed).isNotSameInstanceAs(fiasp)
    }

    @Test
    fun `initWorkflow keeps the already loaded insulins on a second run`() = runTest(testDispatcher) {
        stubWorkflow(insulins = listOf(fiasp, lyumjev))
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        // Second pass: loadInsulins() short-circuits on a non-empty list, so a changed manager is ignored.
        whenever(insulinManager.insulins).thenReturn(arrayListOf(ICfg("Novorapid", 75, 5.0, 1.0)))
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.availableInsulins.value.map { it.insulinLabel }).containsExactly("Fiasp", "Lyumjev").inOrder()
    }

    @Test
    fun `initWorkflow resumes at the safety check for the safety check entry point`() = runTest(testDispatcher) {
        stubWorkflow(siteRotation = false)

        sut.initWorkflow(CarelevoScreenType.SAFETY_CHECK)
        advanceUntilIdle()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.SAFETY_CHECK)
        assertThat(sut.currentStepIndex.value).isEqualTo(3)
    }

    @Test
    fun `initWorkflow resumes at patch attach for the needle insertion entry point`() = runTest(testDispatcher) {
        stubWorkflow(siteRotation = false)

        sut.initWorkflow(CarelevoScreenType.NEEDLE_INSERTION)
        advanceUntilIdle()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_ATTACH)
        assertThat(sut.currentStepIndex.value).isEqualTo(4)
    }

    @Test
    fun `initWorkflow resets a site placement chosen by a previous run`() = runTest(testDispatcher) {
        stubWorkflow()
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
    }

    // ---- step advancement ---------------------------------------------------------------------

    @Test
    fun `advanceFromSafetyCheck goes straight to patch attach when site rotation is off`() {
        sut.advanceFromSafetyCheck()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_ATTACH)
    }

    @Test
    fun `advanceFromSafetyCheck routes through site location when it is part of the run`() = runTest(testDispatcher) {
        stubWorkflow(siteRotation = true)
        whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<Boolean>())).thenReturn(emptyList())
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        sut.advanceFromSafetyCheck()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.SITE_LOCATION)
        assertThat(sut.currentStepIndex.value).isEqualTo(4)
    }

    @Test
    fun `confirmAmount commits the fill amount and advances past the set amount step`() = runTest(testDispatcher) {
        stubWorkflow()
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        sut.confirmAmount(200)

        assertThat(sut.inputInsulin).isEqualTo(200)
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_CONNECT)
        assertThat(sut.currentStepIndex.value).isEqualTo(2)
    }

    // ---- insulin selection --------------------------------------------------------------------

    @Test
    fun `selectInsulin records the chosen insulin`() {
        sut.selectInsulin(lyumjev)

        assertThat(sut.selectedInsulin.value).isEqualTo(lyumjev)
    }

    @Test
    fun `advanceFromInsulin commits the profile switch before advancing when the insulin changed`() = runTest(testDispatcher) {
        val effective = mock<EffectiveProfile> { on { iCfg } doReturn fiasp }
        whenever(profileFunction.getProfile()).thenReturn(effective)
        sut.selectInsulin(lyumjev)

        sut.advanceFromInsulin()
        advanceUntilIdle()

        verifyBlocking(profileFunction) { createProfileSwitchWithNewInsulin(eq(lyumjev), eq(Sources.Carelevo)) }
    }

    @Test
    fun `advanceFromInsulin skips the profile switch when the active insulin is already selected`() = runTest(testDispatcher) {
        val effective = mock<EffectiveProfile> { on { iCfg } doReturn fiasp }
        whenever(profileFunction.getProfile()).thenReturn(effective)
        sut.selectInsulin(fiasp)

        sut.advanceFromInsulin()
        advanceUntilIdle()

        verifyBlocking(profileFunction, never()) { createProfileSwitchWithNewInsulin(any(), any()) }
    }

    @Test
    fun `advanceFromInsulin advances without touching the profile when nothing is selected`() = runTest(testDispatcher) {
        sut.advanceFromInsulin()
        advanceUntilIdle()

        verifyBlocking(profileFunction, never()) { createProfileSwitchWithNewInsulin(any(), any()) }
        verifyBlocking(profileFunction, never()) { getProfile() }
    }

    @Test
    fun `advanceFromInsulin moves to the step following the insulin choice`() = runTest(testDispatcher) {
        stubWorkflow(insulins = listOf(fiasp, lyumjev), activeLabel = "Fiasp")
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.SELECT_INSULIN)

        sut.advanceFromInsulin()
        advanceUntilIdle()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
        assertThat(sut.currentStepIndex.value).isEqualTo(1)
    }

    // ---- ProfileGateStepHost ------------------------------------------------------------------

    @Test
    fun `selectProfile records the chosen profile name`() {
        sut.selectProfile("Night")

        assertThat(sut.selectedProfile.value).isEqualTo("Night")
    }

    @Test
    fun `activateSelectedProfile creates the profile switch and advances past the gate`() = runTest(testDispatcher) {
        stubWorkflow(needsProfileGate = true, insulins = listOf(fiasp))
        stubProfileList(names = listOf("Adult", "Night"), originalName = "Adult")
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PROFILE_GATE)

        val store: ProfileStore = mock()
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(store))
        stubCreateProfileSwitch(mock<PS>())
        sut.selectProfile("Night")

        sut.activateSelectedProfile()
        advanceUntilIdle()

        verifyBlocking(profileFunction) {
            createProfileSwitch(
                eq(store), eq("Night"), eq(0), eq(100), eq(0), any(), any(), eq(Sources.Carelevo), anyOrNull(), any(), any()
            )
        }
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
    }

    @Test
    fun `activateSelectedProfile falls back to the first configured insulin when none is selected`() = runTest(testDispatcher) {
        val store: ProfileStore = mock()
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(store))
        whenever(insulinManager.insulins).thenReturn(arrayListOf(fiasp, lyumjev))
        stubCreateProfileSwitch(mock<PS>())
        sut.selectProfile("Adult")

        sut.activateSelectedProfile()
        advanceUntilIdle()

        verifyBlocking(profileFunction) {
            createProfileSwitch(any(), eq("Adult"), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), eq(fiasp))
        }
    }

    @Test
    fun `activateSelectedProfile logs and stays on the gate when the profile switch cannot be created`() = runTest(testDispatcher) {
        stubWorkflow(needsProfileGate = true, insulins = listOf(fiasp))
        stubProfileList(names = listOf("Night"), originalName = "Night")
        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        val store: ProfileStore = mock()
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(store))
        stubCreateProfileSwitch(null)

        sut.activateSelectedProfile()
        advanceUntilIdle()

        verify(aapsLogger).error(eq(LTag.PUMP), any<String>())
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PROFILE_GATE)
    }

    @Test
    fun `activateSelectedProfile is a no-op when no profile is selected`() = runTest(testDispatcher) {
        sut.activateSelectedProfile()
        advanceUntilIdle()

        verifyNoProfileSwitchCreated()
    }

    @Test
    fun `activateSelectedProfile is a no-op when the profile store is empty`() = runTest(testDispatcher) {
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(null))
        sut.selectProfile("Night")

        sut.activateSelectedProfile()
        advanceUntilIdle()

        verifyNoProfileSwitchCreated()
    }

    @Test
    fun `activateSelectedProfile is a no-op when no insulin is configured at all`() = runTest(testDispatcher) {
        val store: ProfileStore = mock()
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(store))
        whenever(insulinManager.insulins).thenReturn(ArrayList())
        sut.selectProfile("Night")

        sut.activateSelectedProfile()
        advanceUntilIdle()

        verifyNoProfileSwitchCreated()
    }

    @Test
    fun `cancelGate exits the wizard`() = runTest(testDispatcher) {
        val events = collectEvents()

        sut.cancelGate()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.ExitFlow)
    }

    @Test
    fun `exitWizard emits the exit flow event`() = runTest(testDispatcher) {
        val events = collectEvents()

        sut.exitWizard()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.ExitFlow)
    }

    // ---- SiteLocationStepHost -----------------------------------------------------------------

    @Test
    fun `updateSiteLocation publishes the location to the patch keeping the current arrow`() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        verify(carelevoPatch).setSitePlacement(TE.Location.SIDE_RIGHT_UPPER_ARM, TE.Arrow.NONE)
    }

    @Test
    fun `updateSiteArrow publishes the arrow to the patch keeping the current location`() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)

        sut.updateSiteArrow(TE.Arrow.UP)

        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.UP)
        verify(carelevoPatch).setSitePlacement(TE.Location.SIDE_RIGHT_UPPER_ARM, TE.Arrow.UP)
    }

    @Test
    fun `completeSiteLocation republishes the placement and moves to patch attach`() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)
        sut.updateSiteArrow(TE.Arrow.UP)

        sut.completeSiteLocation()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_ATTACH)
        // Once from updateSiteArrow, once from completeSiteLocation itself.
        verify(carelevoPatch, times(2)).setSitePlacement(TE.Location.SIDE_RIGHT_UPPER_ARM, TE.Arrow.UP)
    }

    @Test
    fun `skipSiteLocation clears the placement and moves to patch attach`() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)
        sut.updateSiteArrow(TE.Arrow.UP)

        sut.skipSiteLocation()

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_ATTACH)
        verify(carelevoPatch).setSitePlacement(TE.Location.NONE, TE.Arrow.NONE)
    }

    @Test
    fun `bodyType maps the site rotation user profile preference`() {
        whenever(preferences.get(IntKey.SiteRotationUserProfile)).thenReturn(BodyType.CHILD.value)
        assertThat(sut.bodyType()).isEqualTo(BodyType.CHILD)

        whenever(preferences.get(IntKey.SiteRotationUserProfile)).thenReturn(BodyType.WOMAN.value)
        assertThat(sut.bodyType()).isEqualTo(BodyType.WOMAN)
    }

    @Test
    fun `bodyType falls back to MAN for an unknown preference value`() {
        whenever(preferences.get(IntKey.SiteRotationUserProfile)).thenReturn(99)

        assertThat(sut.bodyType()).isEqualTo(BodyType.MAN)
    }

    // ---- triggerEvent -------------------------------------------------------------------------

    @Test
    fun `triggerEvent forwards the discard complete event verbatim`() = runTest(testDispatcher) {
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectEvent.DiscardComplete)
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardComplete)
    }

    @Test
    fun `triggerEvent forwards the discard failed event verbatim`() = runTest(testDispatcher) {
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectEvent.DiscardFailed)
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardFailed)
    }

    @Test
    fun `triggerEvent collapses an unmapped connect event to NoAction`() = runTest(testDispatcher) {
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectEvent.NoAction)
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.NoAction)
    }

    // ---- startPatchDiscardProcess -------------------------------------------------------------

    @Test
    fun `startPatchDiscardProcess completes immediately when there is no patch state yet`() = runTest(testDispatcher) {
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        verify(patchForceDiscardUseCase, never()).execute()
    }

    @Test
    fun `startPatchDiscardProcess completes immediately for a patch that was never booting`() = runTest(testDispatcher) {
        patchStateSubject.onNext(Optional.of<PatchState>(PatchState.NotConnectedNotBooting))
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardComplete)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `startPatchDiscardProcess routes an active patch through the command queue`() = runTest(testDispatcher) {
        connectedPatch()
        val enact = enactResult(success = true)
        whenever(commandQueue.customCommand(any())).thenReturn(enact)
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue) { customCommand(any()) }
        // The queued CmdDiscard owns unBond + releasePatch; no DB-only fallback on the happy path.
        verify(patchForceDiscardUseCase, never()).execute()
    }

    @Test
    fun `startPatchDiscardProcess falls back to force discard when the queued command fails`() = runTest(testDispatcher) {
        connectedPatch()
        val enact = enactResult(success = false)
        whenever(commandQueue.customCommand(any())).thenReturn(enact)
        val success: ResponseResult<CarelevoUseCaseResponse> = ResponseResult.Success(ResultSuccess)
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(success))
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
        verify(patchForceDiscardUseCase).execute()
        verify(carelevoPatch).discardTeardown()
        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `force discard reports failure and skips teardown when the use case errors`() = runTest(testDispatcher) {
        connectedPatch()
        val enact = enactResult(success = false)
        whenever(commandQueue.customCommand(any())).thenReturn(enact)
        val error: ResponseResult<CarelevoUseCaseResponse> = ResponseResult.Error(RuntimeException("boom"))
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(error))
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verify(carelevoPatch, never()).discardTeardown()
    }

    @Test
    fun `force discard reports failure and skips teardown on a non-success result`() = runTest(testDispatcher) {
        connectedPatch()
        val enact = enactResult(success = false)
        whenever(commandQueue.customCommand(any())).thenReturn(enact)
        val failure: ResponseResult<CarelevoUseCaseResponse> = ResponseResult.Failure("rejected")
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(failure))
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
    }

    @Test
    fun `force discard reports failure when the use case stream itself errors`() = runTest(testDispatcher) {
        connectedPatch()
        val enact = enactResult(success = false)
        whenever(commandQueue.customCommand(any())).thenReturn(enact)
        whenever(patchForceDiscardUseCase.execute())
            .thenReturn(Single.error<ResponseResult<CarelevoUseCaseResponse>>(RuntimeException("stream down")))
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        // doOnError arm: the UI is released and the failure surfaces to the wizard.
        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verify(carelevoPatch, never()).discardTeardown()
    }

    // ---- residual guard branches --------------------------------------------------------------

    @Test
    fun `initWorkflow keeps an already valid profile selection instead of re-reading the active one`() = runTest(testDispatcher) {
        stubWorkflow(needsProfileGate = true)
        val profiles = listOf("Adult", "Night").map { profileName -> mock<SingleProfile> { on { name } doReturn profileName } }
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(profiles))
        // A selection made before the gate is rebuilt is still valid, so it must survive verbatim.
        sut.selectProfile("Night")

        sut.initWorkflow(CarelevoScreenType.CONNECTION_FLOW_START)
        advanceUntilIdle()

        assertThat(sut.selectedProfile.value).isEqualTo("Night")
        verifyBlocking(profileFunction, never()) { getOriginalProfileName() }
    }

    @Test
    fun `initWorkflow starts at the first step for the discard entry point`() = runTest(testDispatcher) {
        stubWorkflow()

        sut.initWorkflow(CarelevoScreenType.PATCH_DISCARD)
        advanceUntilIdle()

        assertThat(sut.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
    }

    @Test
    fun `advanceFromInsulin commits the switch when there is no active profile to compare against`() = runTest(testDispatcher) {
        whenever(profileFunction.getProfile()).thenReturn(null)
        sut.selectInsulin(lyumjev)

        sut.advanceFromInsulin()
        advanceUntilIdle()

        // A null active label can never equal the selection, so the switch is committed before advancing.
        verifyBlocking(profileFunction) { createProfileSwitchWithNewInsulin(eq(lyumjev), eq(Sources.Carelevo)) }
    }

    @Test
    fun `startPatchDiscardProcess completes immediately when the patch state optional is empty`() = runTest(testDispatcher) {
        // Distinct from "no state yet": the subject has a value, but it carries no PatchState.
        patchStateSubject.onNext(Optional.empty<PatchState>())
        val events = collectEvents()

        sut.startPatchDiscardProcess()
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        verify(patchForceDiscardUseCase, never()).execute()
    }
}
