package app.aaps.ui.compose.main

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.ProfileDisplayData
import app.aaps.core.interfaces.overview.graph.RunningModeDisplayData
import app.aaps.core.interfaces.overview.graph.TbrDisplayData
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.icons.IcAction
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcQuickwizard
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.ui.compose.aboutDialog.AboutDialogData
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchResolver
import app.aaps.ui.compose.quickLaunch.QuickLaunchSerializer
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.tempTarget.toTTPresetsWithNameRes
import app.aaps.ui.compose.wizardDialog.showWizardBolusConfirmation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
@Stable
class MainViewModel @Inject constructor(
    private val activePlugin: ActivePlugin,
    val config: Config,
    val preferences: Preferences,
    private val fabricPrivacy: FabricPrivacy,
    private val iconsProvider: IconsProvider,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val overviewDataCache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val profileFunction: ProfileFunction,
    private val constraintChecker: ConstraintsChecker,
    private val quickWizard: QuickWizard,
    private val automation: Automation,
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger,
    private val quickLaunchResolver: QuickLaunchResolver,
    private val wizardExecutor: WizardExecutor,
    private val batchExecutor: BatchExecutor,
    private val uel: UserEntryLogger,
    private val loop: Loop,
    private val protectionCheck: ProtectionCheck,
    private val sceneActions: SceneActions,
    private val sceneChainTargetResolver: SceneChainResolver,
    private val activeSceneManager: ActiveSceneSync,
    private val rxBus: RxBus,
    private val nsClient: NsClient,
    private val visibilityContext: VisibilityContext,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    // Event-driven state (drawer, dialogs, simple-mode preference). Imperative .update{} calls
    // from user actions and preference observers land here.
    private val _eventState = MutableStateFlow(EventState())

    /**
     * AAPSCLIENT-only WS-reachability signal. Constantly true on master. Exposed so the screen
     * can mirror the gating used in [app.aaps.ui.compose.scenes.SceneListViewModel] for any
     * affordance (e.g. the active-scene chip's End button) that would hit the master via the
     * client-control round-trip under the hood.
     */
    val masterReachable: StateFlow<Boolean> = nsClient.masterReachable

    /**
     * AAPSCLIENT-only STABLE pairing signal — always true on a master, on a client true once paired.
     * Drives HIDING of the mutating nav buttons (Treatments + Scenes): an unpaired client cannot command
     * the master, so those entry points are removed entirely (not merely disabled like [masterReachable]).
     * It flips only on an explicit pair/unpair, so it is safe to drive persistent chrome without the
     * flapping [masterReachable] exhibits.
     */
    val masterOrPairedClient: StateFlow<Boolean> = nsClient.masterOrPairedClientFlow

    /** Toolbar items as a separate StateFlow to avoid unnecessary recompositions of the main UI */
    private val _quickLaunchItems = MutableStateFlow<List<ResolvedQuickLaunchItem>>(emptyList())

    /**
     * Public toolbar items. Two layered gates: (1) mode VISIBILITY — actions whose backing element isn't visible
     * now (e.g. MASTER_OR_PAIRED_CLIENT on an unpaired client) are HIDDEN, the same ElementVisibility gate the
     * QuickLaunch config + search use, so a pre-pinned bolus/carbs/scene drops off an unpaired client's toolbar;
     * (2) reachability — scene-action items DISABLE (not hide) while WS is down on AAPSCLIENT. The resolver only
     * sees local catalog state, so both checks layer on here.
     */
    val quickLaunchItems: StateFlow<List<ResolvedQuickLaunchItem>> =
        combine(_quickLaunchItems, masterReachable, masterOrPairedClient) { items, reachable, _ ->
            items
                // (1) Hide mode-gated actions. masterOrPairedClient is combined in only to re-emit on a pairing
                // flip; the predicate reads the current state through visibilityContext.
                .filter { it.action.elementType?.visibility?.isVisible(visibilityContext) ?: true }
                // (2) Scene actions disable while the master is transiently unreachable.
                .map { item ->
                    if (!reachable && item.action is QuickLaunchAction.SceneAction) item.copy(enabled = false) else item
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pending confirmation dialog (automation/TT preset actions) */
    private val _actionConfirmation = MutableStateFlow<ActionConfirmation?>(null)
    val actionConfirmation: StateFlow<ActionConfirmation?> = _actionConfirmation.asStateFlow()

    val versionName: String get() = config.VERSION_NAME
    val appIcon: Int get() = iconsProvider.getIcon()
    val calcProgressFlow: StateFlow<Int> = overviewDataCache.calcProgressFlow

    // Ticker for time-based progress updates (every 30 seconds). Cold flow — only runs while
    // the chipStateFlow it feeds has subscribers (via uiState's WhileSubscribed below).
    private val progressTicker = flow {
        while (true) {
            emit(dateUtil.now())
            delay(30_000L)
        }
    }

    // Flow-derived chip state: ticker + cache flows + expiry detection. Cold.
    private val chipStateFlow: Flow<ChipState> = combine(
        overviewDataCache.tempTargetFlow,
        overviewDataCache.profileFlow,
        overviewDataCache.runningModeFlow,
        overviewDataCache.tbrFlow,
        // Re-emit the latest tick whenever QuickWizard entries change (local edit or synced from the
        // main phone) so the carousel rebuilds. changes is a StateFlow (initial 0) → never blocks.
        combine(progressTicker, quickWizard.changes) { now, _ -> now }
    ) { ttData, profileData, rmData, tbrData, now ->
        buildChipState(ttData, profileData, rmData, tbrData, now)
    }

    /**
     * Derived UI state. Combines event-driven state with ticker-derived chip state.
     * `WhileSubscribed(5_000)` stops the upstream combine (and thus the progressTicker) 5s
     * after the last observer disappears — real battery savings when the overview isn't
     * on screen. 5s grace handles config changes (rotation, dark-mode) without thrashing.
     */
    val uiState: StateFlow<MainUiState> = combine(_eventState, chipStateFlow) { ev, chip ->
        MainUiState(
            isDrawerOpen = ev.isDrawerOpen,
            isSimpleMode = ev.isSimpleMode,
            showAboutDialog = ev.showAboutDialog,
            showMaintenanceSheet = ev.showMaintenanceSheet,
            showAuthFailedDialog = ev.showAuthFailedDialog,
            isProfileLoaded = chip.isProfileLoaded,
            profileName = chip.profileName,
            profilePsId = chip.profilePsId,
            isProfileModified = chip.isProfileModified,
            profileProgress = chip.profileProgress,
            tempTargetText = chip.tempTargetText,
            tempTargetState = chip.tempTargetState,
            tempTargetProgress = chip.tempTargetProgress,
            tempTargetReason = chip.tempTargetReason,
            tempTargetRecordId = chip.tempTargetRecordId,
            runningMode = chip.runningMode,
            runningModeText = chip.runningModeText,
            runningModeRemaining = chip.runningModeRemaining,
            runningModeProgress = chip.runningModeProgress,
            runningModeRecordId = chip.runningModeRecordId,
            tbrState = chip.tbrState,
            smbEnabled = ev.smbEnabled,
            quickWizardItems = chip.quickWizardItems
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        preferences.observe(BooleanKey.GeneralSimpleMode)
            .onEach { simple -> _eventState.update { it.copy(isSimpleMode = simple) } }
            .launchIn(viewModelScope)
        preferences.observe(BooleanKey.ApsUseSmb)
            .onEach { smb -> _eventState.update { it.copy(smbEnabled = smb) } }
            .launchIn(viewModelScope)
        observeQuickLaunch()
    }

    /**
     * Pure transform from raw cache data + tick time to ChipState. Side-effect: calls
     * cache.refresh*() when a row's `timestamp + duration` has passed, since DB-change
     * observers don't emit on expiry.
     */
    private suspend fun buildChipState(
        ttData: TempTargetDisplayData?,
        profileData: ProfileDisplayData?,
        rmData: RunningModeDisplayData?,
        tbrData: TbrDisplayData?,
        now: Long
    ): ChipState {
        // Detect expired chips and schedule a cache refresh. Duration >= 30 days is
        // effectively permanent (e.g. loop disabled uses Int.MAX_VALUE minutes, or scene
        // permanent TT uses Long.MAX_VALUE — avoid Long-overflow in expiry math).
        val ttIsFinite = ttData != null && ttData.state == TempTargetState.ACTIVE
            && ttData.duration > 0 && ttData.duration < T.days(30).msecs()
        val ttExpired = ttIsFinite && now >= ttData.timestamp + ttData.duration
        if (ttExpired) overviewDataCache.refreshTempTarget()

        val profileExpired = profileData != null && profileData.duration > 0
            && now >= profileData.timestamp + profileData.duration
        if (profileExpired) overviewDataCache.refreshProfile()

        val rmIsFinite = rmData != null && rmData.duration > 0 && rmData.duration < T.days(30).msecs()
        val rmExpired = rmIsFinite && now >= rmData.timestamp + rmData.duration
        if (rmExpired) overviewDataCache.refreshRunningMode()

        val tbrExpired = tbrData != null && tbrData.state != TbrState.NONE && tbrData.duration > 0
            && now >= tbrData.timestamp + tbrData.duration
        if (tbrExpired) overviewDataCache.refreshTbr()

        // TT progress and display text
        val ttProgress = if (ttIsFinite && !ttExpired) {
            val elapsed = now - ttData.timestamp
            (elapsed.toFloat() / ttData.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val ttText = if (ttData != null && !ttExpired) {
            if (ttIsFinite) {
                "${ttData.targetRangeText} ${dateUtil.untilString(ttData.timestamp + ttData.duration, rh)}"
            } else {
                ttData.targetRangeText
            }
        } else ""

        // Profile progress and display text
        val profileProgress = if (profileData != null && profileData.duration > 0 && !profileExpired) {
            val elapsed = now - profileData.timestamp
            (elapsed.toFloat() / profileData.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val profileText = if (profileData != null && profileData.profileName.isNotEmpty()) {
            if (profileData.duration > 0 && !profileExpired) {
                "${profileData.profileName} ${dateUtil.untilString(profileData.timestamp + profileData.duration, rh)}"
            } else {
                profileData.profileName
            }
        } else ""

        // Running mode progress and display text
        val rmProgress = if (rmIsFinite && !rmExpired) {
            val elapsed = now - rmData.timestamp
            (elapsed.toFloat() / rmData.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val rmIsTemporaryFinite = rmData != null && rmData.mode.mustBeTemporary() && rmIsFinite && !rmExpired
        // Short remaining time shown on the (label-less) running mode chip, e.g. "30'".
        val rmRemaining = if (rmIsTemporaryFinite) {
            dateUtil.untilString(rmData.timestamp + rmData.duration, rh, withParentheses = false)
        } else ""

        val rmText = if (rmData != null) {
            val modeName = getModeNameString(rmData.mode)
            // Full text kept for the icon's content description (accessibility).
            if (rmIsTemporaryFinite) {
                "$modeName ${dateUtil.untilString(rmData.timestamp + rmData.duration, rh)}"
            } else {
                modeName
            }
        } else ""

        return ChipState(
            isProfileLoaded = profileData?.isLoaded ?: false,
            profileName = profileText,
            profilePsId = profileData?.originalPsId ?: 0,
            isProfileModified = profileData?.isModified ?: false,
            profileProgress = profileProgress,
            tempTargetText = ttText,
            tempTargetState = if (ttExpired) TempTargetChipState.None
            else ttData?.state?.toChipState() ?: TempTargetChipState.None,
            tempTargetProgress = ttProgress,
            tempTargetReason = if (ttExpired) null else ttData?.reason,
            tempTargetRecordId = if (ttExpired) 0 else ttData?.recordId ?: 0,
            runningMode = rmData?.mode ?: RM.Mode.DISABLED_LOOP,
            runningModeText = rmText,
            runningModeRemaining = rmRemaining,
            runningModeProgress = rmProgress,
            runningModeRecordId = if (rmExpired) 0 else rmData?.recordId ?: 0,
            tbrState = if (tbrExpired) TbrState.NONE else tbrData?.state ?: TbrState.NONE,
            quickWizardItems = computeQuickWizardItems(rmData?.mode)
        )
    }

    private suspend fun computeQuickWizardItems(runningMode: RM.Mode?): List<QuickWizardItem> {
        val activeEntries = quickWizard.list().filter { it.isActive() }
        if (activeEntries.isEmpty()) return emptyList()

        val lastBG = iobCobCalculator.ads.lastBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump

        return activeEntries.map { entry ->
            when (entry.mode()) {
                QuickWizardMode.INSULIN -> computeInsulinItem(entry, pump, runningMode)
                QuickWizardMode.CARBS   -> computeCarbsItem(entry)
                QuickWizardMode.WIZARD  -> computeWizardItem(entry, lastBG, profile, profileName, pump, runningMode)
            }
        }
    }

    private fun computeInsulinItem(entry: QuickWizardEntry, pump: Pump, runningMode: RM.Mode?): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()
        val detail = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, entry.insulin())

        val disabledReason = when {
            !pump.isInitialized()                    -> rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
            else                                     -> null
        }
        if (disabledReason != null)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = disabledReason)

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(entry.insulin(), aapsLogger)).value()
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - entry.insulin()) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    private fun computeCarbsItem(entry: QuickWizardEntry): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()
        val detail = rh.gs(app.aaps.core.ui.R.string.format_carbs, entry.carbs())

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    private suspend fun computeWizardItem(
        entry: QuickWizardEntry,
        lastBG: InMemoryGlucoseValue?,
        profile: Profile?,
        profileName: String,
        pump: Pump,
        runningMode: RM.Mode?
    ): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()

        val globalReason = when {
            lastBG == null                           -> rh.gs(app.aaps.core.ui.R.string.wizard_no_actual_bg)
            profile == null                          -> rh.gs(app.aaps.core.ui.R.string.noprofile)
            !pump.isInitialized()                    -> rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
            else                                     -> null
        }
        if (globalReason != null)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, disabledReason = globalReason)

        val wizard = entry.doCalc(profile!!, profileName, lastBG!!)
        if (wizard.calculatedTotalInsulin <= 0.0)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, disabledReason = rh.gs(app.aaps.ui.R.string.wizard_no_insulin_required))

        val detail = rh.gs(app.aaps.core.ui.R.string.format_carbs, entry.carbs()) +
            " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.calculatedTotalInsulin)

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints)
        if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    /**
     * Execute QuickWizard by GUID. WIZARD mode runs the shared prepare→confirm spine (master delivers
     * locally; an AAPSCLIENT routes prepare/commit to its master over the signed round-trip).
     */
    fun executeQuickWizard(guid: String) {
        viewModelScope.launch {
            val entry = quickWizard.get(guid) ?: return@launch
            if (!entry.isActive()) return@launch
            when (entry.mode()) {
                QuickWizardMode.WIZARD  -> executeWizardQuickWizard(entry, guid)
                QuickWizardMode.INSULIN -> executeInsulinMode(entry)
                QuickWizardMode.CARBS   -> executeCarbsMode(entry)
            }
        }
    }

    /**
     * QuickWizard WIZARD mode, role-transparent via [WizardExecutor]: the master computes + caps the dose and authors
     * the confirmation (locally on a master, returned in the signed ack to a client). Both roles render the master's
     * EXACT lines via the shared [showWizardBolusConfirmation]; the user's OK rides back as a commit the master delivers
     * once (the advisor fork chooses correction-only). No dose is ever computed on a client.
     */
    private suspend fun executeWizardQuickWizard(entry: QuickWizardEntry, guid: String) {
        val label = rh.gs(app.aaps.core.ui.R.string.clientcontrol_action_deliver_bolus)
        when (val prepared = wizardExecutor.prepare(WizardExecutor.WizardSource.QuickWizard(guid), label)) {
            is ActionProgress.Prepared ->
                showWizardBolusConfirmation(rxBus, rh, entry.buttonText(), IcQuickwizard, prepared.advisorApplies, prepared.lines, prepared.advisorLines) { asAdvisor ->
                    // The async delivery failure is alarmed once, centrally, from the executor — no local alarm here (it would double).
                    appScope.launch { wizardExecutor.commit(prepared.id, asAdvisor, Sources.QuickWizard, label) }
                }
            // A master-local compute failure (no modal) or a client offline pre-check surfaces here; a client round-trip failure already showed on the app modal.
            is ActionProgress.Rejected ->
                if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                    rxBus.send(EventShowDialog.Ok(title = entry.buttonText(), message = prepared.detail ?: rh.gs(prepared.reason.failTextResId())))

            else                       -> Unit // Unconfirmed → app modal
        }
    }

    /**
     * Prepare→confirm→commit for a FIXED QuickWizard batch (INSULIN or CARBS). Role-transparent via [BatchExecutor]:
     * client → signed round-trip, master → local. The MASTER caps + builds the confirmation; the user confirms the
     * master's exact lines (the contract), then commits. INSULIN now has the client route it previously lacked.
     */
    private suspend fun executeFixedBatch(entry: QuickWizardEntry, actions: List<BatchAction>, label: String, icon: ImageVector) {
        // Tag the bolus action with the originating QuickWizard guid so the MASTER marks the entry used on a successful
        // commit (lastUsed cooldown) and republishes it to clients — the master is SOT. The client must NOT call
        // markAsUsed itself: that writes the Bidirectional QuickWizard pref, which a paired client would push back over
        // the signed round-trip and collide with this commit → "Update settings … Another action is already in progress".
        val tagged = actions.map { if (it is BatchAction.Bolus) it.copy(quickWizardGuid = entry.guid()) else it }
        when (val prepared = batchExecutor.prepare(tagged, Sources.QuickWizard, label)) {
            is ActionProgress.Prepared ->
                rxBus.send(
                    EventShowDialog.OkCancel(
                        title = entry.buttonText(), message = "", confirmationLines = prepared.lines, icon = icon,
                        onOk = {
                            appScope.launch { batchExecutor.commit(prepared.id, Sources.QuickWizard, label) }
                        }
                    )
                )
            // A master-local failure (no modal) or a client offline pre-check surfaces here; a client round-trip failure already showed on the app modal.
            is ActionProgress.Rejected ->
                if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                    rxBus.send(EventShowDialog.Ok(title = entry.buttonText(), message = prepared.detail ?: rh.gs(prepared.reason.failTextResId())))

            else                       -> Unit // Unconfirmed → app modal
        }
    }

    private suspend fun executeInsulinMode(entry: QuickWizardEntry) {
        val insulin = entry.insulin()
        if (insulin <= 0.0) return
        // Raw amount → the master caps + gates; no dose leaves a client.
        executeFixedBatch(
            entry,
            listOf(BatchAction.Bolus(insulin = insulin, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0, recordOnly = false, notes = entry.buttonText(), timestamp = 0L, iCfg = null)),
            rh.gs(app.aaps.core.ui.R.string.bolus),
            IcBolus
        )
    }

    private suspend fun executeCarbsMode(entry: QuickWizardEntry) {
        val carbs = entry.carbs()
        if (carbs <= 0) return
        val hasEcarbs = entry.useEcarbs() == QuickWizardEntry.YES
        executeFixedBatch(
            entry,
            listOf(BatchAction.Bolus(
                insulin = 0.0, carbs = carbs, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
                recordOnly = false, notes = entry.buttonText(), timestamp = 0L, iCfg = null,
                eCarbsGrams = if (hasEcarbs) entry.carbs2() else 0,
                eCarbsDelayMinutes = if (hasEcarbs) entry.time() else 0,
                eCarbsDurationHours = if (hasEcarbs) entry.duration() else 0
            )),
            rh.gs(app.aaps.core.ui.R.string.carbs),
            IcCarbs
        )
    }

    /**
     * Get localized name string for running mode
     */
    private fun getModeNameString(mode: RM.Mode): String = when (mode) {
        RM.Mode.CLOSED_LOOP       -> rh.gs(app.aaps.core.ui.R.string.closedloop)
        RM.Mode.CLOSED_LOOP_LGS   -> rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)
        RM.Mode.OPEN_LOOP         -> rh.gs(app.aaps.core.ui.R.string.openloop)
        RM.Mode.DISABLED_LOOP     -> rh.gs(app.aaps.core.ui.R.string.disabled_loop)
        RM.Mode.SUPER_BOLUS       -> rh.gs(app.aaps.core.ui.R.string.superbolus)
        RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
        RM.Mode.SUSPENDED_BY_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_suspended)
        RM.Mode.SUSPENDED_BY_USER -> rh.gs(app.aaps.core.ui.R.string.loopsuspended)
        RM.Mode.SUSPENDED_BY_DST  -> rh.gs(app.aaps.core.ui.R.string.loop_suspended_by_dst)
        RM.Mode.RESUME            -> rh.gs(app.aaps.core.ui.R.string.resumeloop)
    }

    // Map cache state to UI chip state
    private fun TempTargetState.toChipState(): TempTargetChipState = when (this) {
        TempTargetState.NONE     -> TempTargetChipState.None
        TempTargetState.ACTIVE   -> TempTargetChipState.Active
        TempTargetState.ADJUSTED -> TempTargetChipState.Adjusted
    }

    // Drawer state
    fun openDrawer() {
        _eventState.update { it.copy(isDrawerOpen = true) }
    }

    fun closeDrawer() {
        _eventState.update { it.copy(isDrawerOpen = false) }
    }

    // About dialog state
    fun setShowAboutDialog(show: Boolean) {
        _eventState.update { it.copy(showAboutDialog = show) }
    }

    fun setShowMaintenanceSheet(show: Boolean) {
        _eventState.update { it.copy(showMaintenanceSheet = show) }
    }

    fun setShowAuthFailedDialog(show: Boolean) {
        _eventState.update { it.copy(showAuthFailedDialog = show) }
    }

    // Build about dialog data
    fun buildAboutDialogData(appName: String): AboutDialogData {
        var message = "Build: ${config.BUILD_VERSION}\n"
        message += "Flavor: ${config.FLAVOR}${config.BUILD_TYPE}\n"
        message += "${rh.gs(app.aaps.core.ui.R.string.configbuilder_nightscoutversion_label)} ${nsClient.detectedNsVersion() ?: rh.gs(app.aaps.core.ui.R.string.not_available_full)}"
        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
        val enabledOptions = ExternalOptions.entries.filter { config.isEnabled(it) }
        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)

        return AboutDialogData(
            title = "$appName ${config.VERSION}",
            message = message,
            icon = iconsProvider.getIcon(),
            enabledOptions = enabledOptions
        )
    }

    // ── Toolbar ──

    private fun observeQuickLaunch() {
        preferences.observe(StringNonKey.QuickLaunchActions)
            .onEach { refreshQuickLaunch(it) }
            .launchIn(viewModelScope)
    }

    /**
     * Load toolbar actions from preferences, validate dynamic entries, and resolve display info.
     * Call this on init and whenever relevant data changes (preferences, automations, profiles, etc.)
     */
    fun refreshQuickLaunch(json: String = preferences.get(StringNonKey.QuickLaunchActions)) {
        val actions = QuickLaunchSerializer.fromJson(json)

        // Validate dynamic actions and collect valid ones
        val validated = actions.filter { action -> quickLaunchResolver.isValid(action) }

        // If validation removed items, persist the cleaned list
        if (validated.size != actions.size) {
            preferences.put(StringNonKey.QuickLaunchActions, QuickLaunchSerializer.toJson(validated))
        }

        // Resolve display properties
        _quickLaunchItems.update { validated.map { quickLaunchResolver.resolveItem(it) } }
    }

    fun requestAutomationConfirmation(automationId: String) {
        val event = automation.findEventById(automationId) ?: return
        val message = event.actionsDescription().joinToString("\n") { "• $it" }
        _actionConfirmation.update {
            ActionConfirmation(
                title = event.title,
                message = message,
                icon = IcAutomation,
                onConfirmAction = ConfirmableAction.ExecuteAutomation(automationId)
            )
        }
    }

    /** QuickLaunch TT preset → contact the master, render the master's confirmation, commit on OK (role-transparent). */
    fun requestTempTargetPresetConfirmation(presetId: String) {
        val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresetsWithNameRes()
        val preset = presets.find { it.id == presetId } ?: return
        val icon = when (preset.reason) {
            TT.Reason.ACTIVITY     -> IcTtActivity
            TT.Reason.EATING_SOON  -> IcTtEatingSoon
            TT.Reason.HYPOGLYCEMIA -> IcTtHypo
            else                   -> IcTtManual
        }
        val label = rh.gs(app.aaps.core.ui.R.string.clientcontrol_action_set_temp_target)
        val actions = listOf(BatchAction.TempTarget(preset.reason.text, preset.targetValue, preset.targetValue, (preset.duration / 60000L).toInt(), 0))
        viewModelScope.launch {
            when (val prepared = batchExecutor.prepare(actions, Sources.TTDialog, label)) {
                is ActionProgress.Prepared ->
                    rxBus.send(
                        EventShowDialog.OkCancel(
                            title = rh.gs(app.aaps.core.ui.R.string.temporary_target),
                            message = "",
                            confirmationLines = prepared.lines,
                            icon = icon,
                            onOk = { appScope.launch { batchExecutor.commit(prepared.id, Sources.TTDialog, label) } })
                    )

                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.temporary_target), message = prepared.detail ?: rh.gs(prepared.reason.failTextResId())))

                else                       -> Unit
            }
        }
    }

    /** QuickLaunch profile switch → contact the master, render the master's confirmation, commit on OK (role-transparent). */
    fun requestProfileConfirmation(profileName: String, percentage: Int, durationMinutes: Int) {
        val label = rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch)
        val actions = listOf(BatchAction.ProfileSwitch(percentage, 0, durationMinutes, profileName = profileName))
        viewModelScope.launch {
            when (val prepared = batchExecutor.prepare(actions, Sources.ProfileSwitchDialog, label)) {
                is ActionProgress.Prepared ->
                    rxBus.send(EventShowDialog.OkCancel(title = label, message = "", confirmationLines = prepared.lines, icon = IcProfile, onOk = { appScope.launch { batchExecutor.commit(prepared.id, Sources.ProfileSwitchDialog, label) } }))

                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowDialog.Ok(title = label, message = prepared.detail ?: rh.gs(prepared.reason.failTextResId())))

                else                       -> Unit
            }
        }
    }

    fun showTbrInfo() {
        viewModelScope.launch {
            val activeTb = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())
            val profile = profileFunction.getProfile()
            val title: String
            val message: String
            if (activeTb != null && profile != null) {
                title = rh.gs(app.aaps.core.ui.R.string.temp_basal)
                message = activeTb.toStringFull(profile, dateUtil, rh)
            } else {
                title = rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label)
                message = if (profile != null)
                    rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())
                else
                    rh.gs(app.aaps.ui.R.string.no_temp_basal_running)
            }
            rxBus.send(EventShowDialog.Ok(title = title, message = message))
        }
    }

    fun performLoopAccept() {
        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
            if (result == ProtectionResult.GRANTED) {
                viewModelScope.launch {
                    val lastRun = loop.lastRun ?: return@launch
                    if (lastRun.constraintsProcessed?.isChangeRequested() == true) {
                        uel.log(Action.ACCEPTS_TEMP_BASAL, Sources.Overview)
                        loop.invoke("Accept temp button", false)
                        loop.acceptChangeRequest()
                    }
                }
            }
        }
    }

    /** Expose active scene state for UI (banner, etc.) */
    val activeSceneState: StateFlow<ActiveSceneState?> = activeSceneManager.activeSceneState

    /** Whether the active scene has expired (duration ran out, non-duration actions reverted).
     *  Derived from the lifecycle field that lives inside [ActiveSceneState] and rides NS sync. */
    val sceneExpired: StateFlow<Boolean> = activeSceneManager.activeSceneState
        .map { it?.lifecycle == SceneLifecycle.EXPIRED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Dismiss the expired scene banner.
     *
     *  On AAPSCLIENT: send scene_stop to master. Master's [SceneAutomationApiImpl.stopActiveScene]
     *  routes expired scenes to [SceneExecutor.dismiss], then republishes empty `activeScene` via
     *  NS — clients see banner gone. Local dismissal alone would be undone on the next
     *  RunningConfiguration apply (master still has lifecycle=EXPIRED).
     *
     *  On master: dismiss locally as before. */
    fun dismissExpiredScene() {
        // stop(false) maps to "dismiss expired banner" on the master (stopActiveScene dismisses when
        // the scene is expired) and to scene.stop on a client — one path for both.
        viewModelScope.launch { sceneActions.stop(triggerChain = false) }
    }

    /** Format milliseconds to a localized "time remaining" string (e.g., "1h 30m remaining"). */
    fun formatDuration(ms: Long): String = dateUtil.timeRemainingString(ms, rh)

    /** QuickLaunch scene → ask the MASTER to PREPARE it, render the master's authored confirmation lines, commit on OK (role-transparent). */
    fun requestSceneConfirmation(sceneId: String) {
        val title = rh.gs(app.aaps.core.ui.R.string.scene)
        viewModelScope.launch {
            when (val prepared = sceneActions.prepareStart(sceneId)) {
                is ActionProgress.Prepared ->
                    rxBus.send(
                        EventShowDialog.OkCancel(
                            // commitStart uses the executor's consume-once prepared.id token, so a double
                            // onOk (fast double-tap) is idempotent — the second commit hits an already-
                            // consumed id and is discarded.
                            title = title, message = "", confirmationLines = prepared.lines, icon = IcAction,
                            onOk = { appScope.launch { sceneActions.commitStart(prepared.id) } })
                    )

                is ActionProgress.Rejected ->
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowDialog.Ok(title = title, message = prepared.detail ?: rh.gs(prepared.reason.failTextResId())))

                else                       -> Unit
            }
        }
    }

    fun requestSceneDeactivation() = viewModelScope.launch {
        val activeState = activeSceneManager.getActiveState() ?: return@launch
        val message = rh.gs(app.aaps.core.ui.R.string.scene_confirm_deactivate, activeState.scene.name)
        // When the scene chains to a runnable follow-up, "Skip to <Next>" becomes the primary
        // action and "End Scene" the alternative — the chain represents the user's pre-declared
        // intent for what happens next, so it's the recommended path on early end. On master,
        // preconditions are re-checked at execute time as a TOCTOU guard inside the resolver;
        // on AAPSClient we use the catalog-only check (the local pump/loop/profile don't
        // reflect master state — see SceneChainTargetResolver KDoc).
        val target = if (config.AAPSCLIENT) sceneChainTargetResolver.resolveCatalogChainTarget(activeState.scene)
        else sceneChainTargetResolver.resolveRunnableChainTarget(activeState.scene)
        _actionConfirmation.update {
            if (target != null) ActionConfirmation(
                title = rh.gs(app.aaps.core.ui.R.string.scene_deactivate),
                message = message,
                icon = IcAction,
                onConfirmAction = ConfirmableAction.DeactivateAndChainScene(target.id),
                confirmLabel = rh.gs(app.aaps.core.ui.R.string.scene_skip_to_format, target.name),
                secondaryAction = ConfirmableAction.DeactivateScene,
                secondaryLabel = rh.gs(app.aaps.core.ui.R.string.scene_deactivate)
            )
            else ActionConfirmation(
                title = rh.gs(app.aaps.core.ui.R.string.scene_deactivate),
                message = message,
                icon = IcAction,
                onConfirmAction = ConfirmableAction.DeactivateScene
            )
        }
    }

    fun dismissActionConfirmation() {
        _actionConfirmation.update { null }
    }

    fun executeConfirmableAction(action: ConfirmableAction) = viewModelScope.launch {
        _actionConfirmation.update { null }
        when (action) {
            is ConfirmableAction.ExecuteAutomation       -> {
                val event = automation.findEventById(action.automationId) ?: return@launch
                viewModelScope.launch { automation.processEvent(event) }
            }

            is ConfirmableAction.DeactivateScene         ->
                sceneActions.stop(triggerChain = false)

            is ConfirmableAction.DeactivateAndChainScene ->
                // The master derives the chain target from its active scene's endAction and posts the
                // SCENE_CHAINED / SCENE_CHAIN_ERROR notification itself (in SceneAutomationApiImpl.
                // stopActiveSceneAndChain), so this is identical for UI- and client-triggered chains.
                sceneActions.stop(triggerChain = true)
        }
    }

}

/**
 * Event-driven subset of MainUiState: updated imperatively by user actions and preference
 * observers. Kept in a MutableStateFlow because these fields are not derived from other flows.
 */
private data class EventState(
    val isDrawerOpen: Boolean = false,
    val isSimpleMode: Boolean = true,
    val smbEnabled: Boolean = false,
    val showAboutDialog: Boolean = false,
    val showMaintenanceSheet: Boolean = false,
    val showAuthFailedDialog: Boolean = false
)

/**
 * Flow-derived subset of MainUiState: produced by combining the cache flows with the 30s
 * progressTicker. Computed declaratively so the ticker auto-pauses via WhileSubscribed when
 * uiState has no observers.
 */
private data class ChipState(
    val isProfileLoaded: Boolean = false,
    val profileName: String = "",
    val profilePsId: Long = 0,
    val isProfileModified: Boolean = false,
    val profileProgress: Float = 0f,
    val tempTargetText: String = "",
    val tempTargetState: TempTargetChipState = TempTargetChipState.None,
    val tempTargetProgress: Float = 0f,
    val tempTargetReason: TT.Reason? = null,
    val tempTargetRecordId: Long = 0,
    val runningMode: RM.Mode = RM.Mode.DISABLED_LOOP,
    val runningModeText: String = "",
    val runningModeRemaining: String = "",
    val runningModeProgress: Float = 0f,
    val runningModeRecordId: Long = 0,
    val tbrState: TbrState = TbrState.NONE,
    val quickWizardItems: List<QuickWizardItem> = emptyList()
)
