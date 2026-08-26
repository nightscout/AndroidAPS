package app.aaps.pump.carelevo.compose.patchflow

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Optional

/**
 * Compose render tests for the activation wizard host [CarelevoPatchFlowScreen]: step routing, the
 * exit/discard event plumbing and the Loading scrim.
 *
 * **How the four ViewModels are injected.** The screen resolves them with a bare `hiltViewModel()`,
 * so there is no parameter to pass a double through. `hiltViewModel()` reads
 * `LocalViewModelStoreOwner`, wraps that owner's default factory in
 * `androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory(context, delegate)` and hands the result to
 * `viewModel()`. That wrapper walks up to the hosting Activity and pulls the Hilt
 * `ActivityCreatorEntryPoint` off it — which blows up under the plain `ComponentActivity` that
 * `createComposeRule()` launches, since it is not an `@AndroidEntryPoint`. [ShadowHiltViewModelFactory]
 * replaces that one static wrapper with an identity function, so the delegate factory is used verbatim;
 * [TestViewModelStoreOwner] then serves the real ViewModels (built here with mocked collaborators, the
 * same way `CarelevoPatchConnectionFlowViewModelTest` does) straight out of its store. Only the
 * `HiltViewModelFactory` class itself is instrumented — see `instrumentedPackages`, which is a
 * `startsWith` match, not a package match.
 *
 * **Coroutines.** `viewModelScope` dispatches on `Dispatchers.Main`, replaced here by an
 * [UnconfinedTestDispatcher] so a ViewModel call made from a test body has already settled by the time
 * it returns. Composition keeps the rule's own dispatcher; `compose.waitForIdle()` bridges the two
 * whenever an event has to travel from a ViewModel into the composition.
 *
 * **Rx.** [AapsSchedulers.io] / `main` are the trampoline so the force-discard chain runs inline. The
 * Loading tests exploit that chain: `startPatchDiscardProcess` on a connected patch sets Loading, the
 * queued `CmdDiscard` is stubbed to fail, and the force-discard fallback returns `Single.never()` — so
 * the ViewModel parks in [app.aaps.pump.carelevo.common.model.UiState.Loading] and the scrim stays up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    shadows = [ShadowHiltViewModelFactory::class],
    instrumentedPackages = ["androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory"]
)
class CarelevoPatchFlowScreenTest {

    @get:Rule
    val compose = createComposeRule()

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
    private lateinit var sp: SP
    private lateinit var bleSession: CarelevoBleSession
    private lateinit var transport: CarelevoBleTransport
    private lateinit var scanner: BleScanner
    private lateinit var connectNewPatchUseCase: CarelevoConnectNewPatchUseCase
    private lateinit var pumpSync: PumpSync
    private lateinit var carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase
    private lateinit var activationExecutor: CarelevoActivationExecutor

    private lateinit var patchStateSubject: BehaviorSubject<Optional<PatchState>>
    private lateinit var patchInfoSubject: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>>

    private lateinit var flowViewModel: CarelevoPatchConnectionFlowViewModel
    private lateinit var connectViewModel: CarelevoPatchConnectViewModel
    private lateinit var needleViewModel: CarelevoPatchNeedleInsertionViewModel
    private lateinit var safetyCheckViewModel: CarelevoPatchSafetyCheckViewModel
    private lateinit var viewModelStoreOwner: TestViewModelStoreOwner

    private val snackbarHostState = SnackbarHostState()
    private var toolbar: ToolbarConfig? = null
    private var exitCount = 0

    private val fiasp = ICfg(insulinLabel = "Fiasp", peak = 55, dia = 6.0, concentration = 1.0)
    private val lyumjev = ICfg(insulinLabel = "Lyumjev", peak = 45, dia = 5.0, concentration = 1.0)

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun string(id: Int): String = context.getString(id)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The force-discard chain ends in subscribe(onSuccess) with no onError arm; swallow the
        // undeliverable OnErrorNotImplementedException a late timeout would route to the plugin.
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
        sp = mock()
        bleSession = mock()
        transport = mock()
        scanner = mock()
        connectNewPatchUseCase = mock()
        pumpSync = mock()
        carelevoAlarmInfoUseCase = mock()
        activationExecutor = mock()

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())

        patchStateSubject = BehaviorSubject.create()
        patchInfoSubject = BehaviorSubject.create()
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject)
        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoSubject)
        whenever(transport.scanner).thenReturn(scanner)

        // AapsTheme resolves the dark-mode preference; pin it so the colour scheme is deterministic.
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
        whenever(preferences.get(BooleanKey.GeneralInsulinConcentration)).thenReturn(false)
        whenever(preferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(false)
        whenever(preferences.get(IntKey.SiteRotationUserProfile)).thenReturn(BodyType.MAN.value)
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(emptyList()))

        flowViewModel = CarelevoPatchConnectionFlowViewModel(
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
        connectViewModel = CarelevoPatchConnectViewModel(
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            sp = sp,
            bleSession = bleSession,
            transport = transport,
            connectNewPatchUseCase = connectNewPatchUseCase,
            patchForceDiscardUseCase = patchForceDiscardUseCase
        )
        needleViewModel = CarelevoPatchNeedleInsertionViewModel(
            aapsLogger = aapsLogger,
            pumpSync = pumpSync,
            persistenceLayer = persistenceLayer,
            aapsSchedulers = aapsSchedulers,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            patchForceDiscardUseCase = patchForceDiscardUseCase,
            carelevoAlarmInfoUseCase = carelevoAlarmInfoUseCase
        )
        safetyCheckViewModel = CarelevoPatchSafetyCheckViewModel(
            aapsSchedulers = aapsSchedulers,
            aapsLogger = aapsLogger,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            activationExecutor = activationExecutor,
            patchForceDiscardUseCase = patchForceDiscardUseCase
        )
        viewModelStoreOwner = TestViewModelStoreOwner(
            mapOf(
                CarelevoPatchConnectionFlowViewModel::class.java to flowViewModel,
                CarelevoPatchConnectViewModel::class.java to connectViewModel,
                CarelevoPatchNeedleInsertionViewModel::class.java to needleViewModel,
                CarelevoPatchSafetyCheckViewModel::class.java to safetyCheckViewModel
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        RxJavaPlugins.reset()
    }

    // ---- harness ------------------------------------------------------------------------------

    private fun setFlowContent(screenType: CarelevoScreenType = CarelevoScreenType.CONNECTION_FLOW_START) {
        compose.setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                AapsTheme {
                    CarelevoPatchFlowScreen(
                        screenType = screenType,
                        setToolbarConfig = { toolbar = it },
                        snackbarHostState = snackbarHostState,
                        onExitFlow = { exitCount++ }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Park the wizard on [step] without letting the screen's init effect rebuild the workflow: the
     * one-shot latch is what the screen checks, so pre-latching it pins the page the test asked for.
     */
    private fun startAt(step: CarelevoPatchStep) {
        flowViewModel.setIsCreated(true)
        flowViewModel.setPage(step)
        setFlowContent()
    }

    /** Stub the collaborators `initWorkflow` fans out to. The `suspend` ones need a coroutine body. */
    private fun stubWorkflow(
        needsProfileGate: Boolean = false,
        siteRotation: Boolean = false,
        insulins: List<ICfg> = listOf(fiasp),
        activeLabel: String? = "Fiasp"
    ) {
        runBlocking {
            val requested: PS? = if (needsProfileGate) null else mock<PS>()
            whenever(profileFunction.getRequestedProfile()).thenReturn(requested)
            whenever(preferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(siteRotation)
            whenever(insulinManager.insulins).thenReturn(ArrayList(insulins))
            val effective: EffectiveProfile? =
                activeLabel?.let { label -> mock { on { iCfg } doReturn ICfg(label, 55, 6.0, 1.0) } }
            whenever(profileFunction.getProfile()).thenReturn(effective)
            whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<Boolean>())).thenReturn(emptyList())
        }
    }

    private fun stubProfileList(names: List<String>, originalName: String) {
        runBlocking {
            val profiles = names.map { profileName -> mock<SingleProfile> { on { name } doReturn profileName } }
            whenever(profileRepository.profiles).thenReturn(MutableStateFlow(profiles))
            whenever(profileFunction.getOriginalProfileName()).thenReturn(originalName)
        }
    }

    /** Stub the queued command result. `customCommand` is `suspend`, so it stubs through `onBlocking`. */
    private fun stubCustomCommand(success: Boolean) {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        commandQueue.stub { onBlocking { customCommand(any()) } doReturn result }
    }

    /**
     * Drive any of the four ViewModels into `UiState.Loading` and keep it there: the queued discard
     * fails, and the DB-only fallback it routes to never emits.
     */
    private fun armStuckDiscard() {
        patchStateSubject.onNext(Optional.of<PatchState>(PatchState.ConnectedBooted))
        stubCustomCommand(success = false)
        whenever(patchForceDiscardUseCase.execute())
            .thenReturn(Single.never<ResponseResult<CarelevoUseCaseResponse>>())
    }

    private fun assertLoadingScrimShown() {
        compose.onNodeWithText(string(CoreUiR.string.loading)).assertIsDisplayed()
    }

    // ---- step routing -------------------------------------------------------------------------

    @Test
    fun profileGateStep_rendersProfilePicker() {
        stubWorkflow(needsProfileGate = true)
        stubProfileList(names = listOf("Adult", "Night"), originalName = "Adult")

        setFlowContent()

        assertThat(toolbar?.title).isEqualTo(string(CoreUiR.string.pump_wizard_profile_gate_title))
        compose.onNodeWithText(string(CoreUiR.string.pump_wizard_profile_gate_pick)).assertIsDisplayed()
        compose.onNodeWithText("Adult").assertIsDisplayed()
        compose.onNodeWithText("Night").assertIsDisplayed()
        compose.onNodeWithText(string(CoreUiR.string.activate_profile)).assertIsEnabled()
    }

    @Test
    fun profileGateStep_activateClick_createsProfileSwitchAndAdvances() {
        stubWorkflow(needsProfileGate = true)
        stubProfileList(names = listOf("Adult", "Night"), originalName = "Adult")
        val store: ProfileStore = mock()
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(store))
        // Build the result eagerly: defining a mock inside an in-flight whenever() trips
        // UnfinishedStubbingException.
        val switched: PS = mock()
        runBlocking {
            whenever(
                profileFunction.createProfileSwitch(
                    any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any()
                )
            ).thenReturn(switched)
        }
        setFlowContent()

        compose.onNodeWithText("Night").performClick()
        compose.onNodeWithText(string(CoreUiR.string.activate_profile)).performClick()
        compose.waitForIdle()

        verifyBlocking(profileFunction) {
            createProfileSwitch(any(), eq("Night"), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())
        }
        assertThat(flowViewModel.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
    }

    @Test
    fun profileGateStep_cancelClick_exitsFlow() {
        stubWorkflow(needsProfileGate = true)
        stubProfileList(names = listOf("Adult"), originalName = "Adult")
        setFlowContent()

        compose.onNodeWithText(string(CoreUiR.string.cancel)).performClick()
        compose.waitForIdle()

        assertThat(exitCount).isEqualTo(1)
        assertThat(flowViewModel.isCreated).isFalse()
    }

    @Test
    fun selectInsulinStep_rendersWhenMoreThanOneInsulinIsConfigured() {
        stubWorkflow(insulins = listOf(fiasp, lyumjev), activeLabel = "Fiasp")

        setFlowContent()

        assertThat(toolbar?.title).isEqualTo(string(CoreUiR.string.select_insulin))
        assertThat(flowViewModel.page.value).isEqualTo(CarelevoPatchStep.SELECT_INSULIN)
        compose.onNodeWithText("Lyumjev").assertIsDisplayed()
        compose.onNodeWithText(string(CoreUiR.string.next)).assertIsEnabled()
    }

    @Test
    fun patchStartStep_rendersAndNextAdvancesToSetAmount() {
        stubWorkflow(insulins = listOf(fiasp))

        setFlowContent()

        assertThat(toolbar?.title).isEqualTo(string(R.string.carelevo_connect_prepare_title))
        compose.onNodeWithText(string(R.string.carelevo_title_fill_insulin)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.carelevo_btn_insulin_guide)).assertIsDisplayed()

        compose.onNodeWithText(string(CoreUiR.string.next)).performClick()
        compose.waitForIdle()

        assertThat(flowViewModel.page.value).isEqualTo(CarelevoPatchStep.SET_AMOUNT)
        assertThat(toolbar?.title).isEqualTo(string(R.string.patch_prepare_dialog_title_insulin_amount))
    }

    @Test
    fun patchStartStep_cancelClick_exitsFlowAndClearsCreatedLatch() {
        stubWorkflow(insulins = listOf(fiasp))
        setFlowContent()
        assertThat(flowViewModel.isCreated).isTrue()

        compose.onNodeWithText(string(CoreUiR.string.cancel)).performClick()
        compose.waitForIdle()

        assertThat(exitCount).isEqualTo(1)
        // Without the latch reset a second activation would resume mid-flow against a fresh patch.
        assertThat(flowViewModel.isCreated).isFalse()
    }

    @Test
    fun setAmountStep_renders() {
        startAt(CarelevoPatchStep.SET_AMOUNT)

        assertThat(toolbar?.title).isEqualTo(string(R.string.patch_prepare_dialog_title_insulin_amount))
        compose.onNodeWithText(string(CoreUiR.string.next)).assertIsEnabled()
        compose.onNodeWithText(string(CoreUiR.string.cancel)).assertIsEnabled()
    }

    @Test
    fun patchConnectStep_rendersAndResetsTheConnectViewModelOnEnter() {
        startAt(CarelevoPatchStep.PATCH_CONNECT)

        assertThat(toolbar?.title).isEqualTo(string(R.string.carelevo_connect_patch_title))
        compose.onNodeWithText(string(R.string.carelevo_patch_connect_step_1_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.carelevo_btn_input_search_patch)).assertIsEnabled()
        // The page-entry effect must clear any scan left running by a previous visit.
        verify(scanner).stopScan()
        assertThat(connectViewModel.isScanWorking).isFalse()
    }

    @Test
    fun safetyCheckStep_rendersFromTheSafetyCheckEntryPoint() {
        stubWorkflow()

        setFlowContent(CarelevoScreenType.SAFETY_CHECK)

        assertThat(flowViewModel.page.value).isEqualTo(CarelevoPatchStep.SAFETY_CHECK)
        assertThat(toolbar?.title).isEqualTo(string(R.string.carelevo_connect_safety_check_title))
        compose.onNodeWithText(string(R.string.carelevo_patch_safety_check_start_desc)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.carelevo_btn_patch_expiration)).assertIsEnabled()
    }

    @Test
    fun siteLocationStep_renders() {
        startAt(CarelevoPatchStep.SITE_LOCATION)

        assertThat(toolbar?.title).isEqualTo(string(CoreUiR.string.site_rotation))
        compose.onNodeWithText(string(CoreUiR.string.skip)).assertIsEnabled()
        // No location picked yet, so the step cannot be completed.
        compose.onNodeWithText(string(CoreUiR.string.next)).assertIsNotEnabled()
    }

    @Test
    fun patchAttachStep_rendersAndNextAdvancesToNeedleInsertion() {
        startAt(CarelevoPatchStep.PATCH_ATTACH)

        assertThat(toolbar?.title).isEqualTo(string(R.string.carelevo_connect_patch_attach_title))
        compose.onNodeWithText(string(R.string.carelevo_patch_attach_step1_title)).assertIsDisplayed()

        compose.onNodeWithText(string(CoreUiR.string.next)).performClick()
        compose.waitForIdle()

        assertThat(flowViewModel.page.value).isEqualTo(CarelevoPatchStep.NEEDLE_INSERTION)
        assertThat(toolbar?.title).isEqualTo(string(R.string.carelevo_connect_needle_check_title))
    }

    @Test
    fun needleInsertionStep_rendersAndObservesPatchInfo() {
        startAt(CarelevoPatchStep.NEEDLE_INSERTION)

        assertThat(toolbar?.title).isEqualTo(string(R.string.carelevo_connect_needle_check_title))
        compose.onNodeWithText(string(R.string.carelevo_patch_needle_insertion_step1_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.carelevo_btn_needle_insert_check)).assertIsEnabled()
        assertThat(needleViewModel.isCreated).isTrue()
        assertThat(patchInfoSubject.hasObservers()).isTrue()
    }

    // ---- loading scrim ------------------------------------------------------------------------

    @Test
    fun loadingScrim_isHiddenWhileEveryViewModelIsIdle() {
        stubWorkflow(insulins = listOf(fiasp))

        setFlowContent()

        compose.onNodeWithText(string(CoreUiR.string.loading)).assertDoesNotExist()
    }

    @Test
    fun loadingScrim_isShownForTheSharedViewModelOutsideTheDedicatedSteps() {
        startAt(CarelevoPatchStep.PATCH_START)
        armStuckDiscard()

        flowViewModel.startPatchDiscardProcess()
        compose.waitForIdle()

        assertLoadingScrimShown()
    }

    @Test
    fun loadingScrim_isShownForTheConnectViewModelOnTheConnectStep() {
        startAt(CarelevoPatchStep.PATCH_CONNECT)
        armStuckDiscard()

        connectViewModel.startPatchDiscardProcess()
        compose.waitForIdle()

        assertLoadingScrimShown()
    }

    @Test
    fun loadingScrim_isShownForTheSafetyCheckViewModelOnTheSafetyCheckStep() {
        startAt(CarelevoPatchStep.SAFETY_CHECK)
        armStuckDiscard()

        safetyCheckViewModel.startDiscardProcess()
        compose.waitForIdle()

        assertLoadingScrimShown()
    }

    @Test
    fun loadingScrim_isShownForTheNeedleViewModelOnTheNeedleStep() {
        startAt(CarelevoPatchStep.NEEDLE_INSERTION)
        armStuckDiscard()

        needleViewModel.startDiscardProcess()
        compose.waitForIdle()

        assertLoadingScrimShown()
    }

    @Test
    fun loadingScrim_ignoresANonActiveViewModelsLoadingState() {
        // The connect ViewModel is loading, but the wizard is parked on a step it does not own.
        startAt(CarelevoPatchStep.PATCH_ATTACH)
        armStuckDiscard()

        connectViewModel.startPatchDiscardProcess()
        compose.waitForIdle()

        compose.onNodeWithText(string(CoreUiR.string.loading)).assertDoesNotExist()
    }

    // ---- exit / discard events ----------------------------------------------------------------

    @Test
    fun discardCompleteEvent_exitsTheFlowAndClearsTheCreatedLatch() {
        startAt(CarelevoPatchStep.PATCH_START)

        // No patch state at all -> the discard short-circuits straight to DiscardComplete.
        flowViewModel.startPatchDiscardProcess()
        compose.waitForIdle()

        assertThat(exitCount).isEqualTo(1)
        assertThat(flowViewModel.isCreated).isFalse()
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun exitFlowEvent_exitsTheFlow() {
        startAt(CarelevoPatchStep.PATCH_START)

        flowViewModel.exitWizard()
        compose.waitForIdle()

        assertThat(exitCount).isEqualTo(1)
        assertThat(flowViewModel.isCreated).isFalse()
    }

    @Test
    fun discardFailedEvent_surfacesTheFailureSnackbarWithoutExiting() {
        startAt(CarelevoPatchStep.PATCH_START)

        flowViewModel.triggerEvent(CarelevoConnectEvent.DiscardFailed)
        compose.waitForIdle()

        assertThat(snackbarHostState.currentSnackbarData?.visuals?.message)
            .isEqualTo(string(R.string.carelevo_toast_msg_discard_failed))
        assertThat(exitCount).isEqualTo(0)
    }

    @Test
    fun unmappedEvent_isIgnored() {
        startAt(CarelevoPatchStep.PATCH_START)

        flowViewModel.triggerEvent(CarelevoConnectEvent.NoAction)
        compose.waitForIdle()

        assertThat(exitCount).isEqualTo(0)
        assertThat(snackbarHostState.currentSnackbarData).isNull()
        assertThat(flowViewModel.isCreated).isTrue()
    }
}

/**
 * Test double registry for `hiltViewModel()`. Implementing [HasDefaultViewModelProviderFactory] is what
 * makes `hiltViewModel()` pick this factory up as its delegate; [ShadowHiltViewModelFactory] then keeps
 * that delegate intact instead of wrapping it in Hilt's activity-bound factory.
 */
private class TestViewModelStoreOwner(
    private val viewModels: Map<Class<out ViewModel>, ViewModel>
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                viewModels[modelClass] as? T ?: error("No test double registered for $modelClass")
        }
}

/**
 * Neutralises `androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory(context, delegateFactory)` — the
 * static wrapper `hiltViewModel()` always routes through. The real one resolves Hilt's
 * `ActivityCreatorEntryPoint` off the hosting Activity, which only exists on an `@AndroidEntryPoint`
 * Activity; the test host is a plain `ComponentActivity`. Returning the delegate verbatim is exactly
 * what the real factory does for any ViewModel outside `@HiltViewModelMap`, so the production lookup
 * path (`viewModel(owner, key, factory)` -> the owner's store) is preserved.
 */
@Implements(className = "androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory", isInAndroidSdk = false)
class ShadowHiltViewModelFactory {

    companion object {

        @JvmStatic
        @Implementation
        fun create(context: Context, delegateFactory: ViewModelProvider.Factory): ViewModelProvider.Factory =
            delegateFactory
    }
}
