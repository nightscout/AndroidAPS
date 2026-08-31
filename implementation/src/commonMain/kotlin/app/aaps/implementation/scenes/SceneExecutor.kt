package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.objects.extensions.profileNames
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.formatMinutesAsDuration
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Executes scene activation and deactivation.
 * Captures prior state before activation for revert on deactivation.
 */
@SingleIn(AppScope::class)
class SceneExecutor @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val preferences: Preferences,
    private val activeSceneManager: ActiveSceneManager,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileUtil: ProfileUtil,
    private val translator: Translator,
    private val profileSwitchSilentGate: ProfileSwitchSilentGate,
    private val notificationManager: NotificationManager,
    private val sceneExpiryScheduler: SceneExpiryScheduler
) {

    /** A parked scene activation awaiting [commitScene] — the two-step master-authoritative path. */
    private data class ParkedScene(val scene: Scene, val durationMinutes: Int, val parkedAt: Long)

    /**
     * Consume-once parked scenes keyed by bolusId (its OWN id-space, separate from the bolus executor's `pending`,
     * so a scene commit and a bolus commit can never drain each other). [take] is atomic → no double-activate.
     *
     * A `ConcurrentHashMap` before, which does not exist outside the JVM. The guarantee that matters here is
     * that a commit either gets the parked scene or gets nothing, so a plain map behind one lock is the same
     * promise; there is no per-entry contention to lose, at most a couple of parks at a time.
     */
    private class ParkedScenes {

        private val lock = AapsLock()
        private val slots = mutableMapOf<Long, ParkedScene>()

        fun park(id: Long, scene: ParkedScene) {
            lock.withLock { slots[id] = scene }
        }

        fun take(id: Long): ParkedScene? = lock.withLock { slots.remove(id) }

        /** Drops parks older than [cutoff], so a prepare the user walked away from cannot be committed later. */
        fun evictParkedBefore(cutoff: Long) {
            lock.withLock { slots.entries.removeAll { it.value.parkedAt < cutoff } }
        }
    }

    private val pendingScenes = ParkedScenes()

    /**
     * Single source of truth for "can this scene activate right now?".
     * Returns null if activation is allowed, otherwise a localized reason
     * describing why it is blocked. Callable from UI for disable state and
     * called defensively by [activate] so non-UI paths (automation rules,
     * future callers) can't bypass the gate.
     *
     * Note: does NOT check [Scene.isEnabled] — that is the user's on/off
     * toggle, surfaced separately in the UI.
     */
    suspend fun validateActivation(scene: Scene): String? {
        if (loop.runningMode().pausesLoopExecution())
            return rh.gs(InterfacesStrings.pump_disconnected)
        if (!activePlugin.activePump.isInitialized() || profileFunction.getProfile() == null)
            return rh.gs(CoreUiStrings.pump_not_initialized_profile_not_set)
        if (scene.actions.isEmpty())
            return rh.gs(CoreUiStrings.scene_no_actions)
        val profileList = profileRepository.profileNames()
        for (action in scene.actions) {
            if (action is SceneAction.ProfileSwitch && action.profileName.isNotEmpty() &&
                action.profileName !in profileList
            ) return rh.gs(CoreUiStrings.scene_profile_not_found, action.profileName)
        }
        return null
    }

    /**
     * Two-step PREPARE (master side): gate the scene, AUTHOR the confirmation lines from its actions (so the client
     * renders the master's exact confirmation, not a client-built one), and park it consume-once. Nothing is
     * activated — a [commitScene] with the returned bolusId does that. Mirrors [WizardBolusExecutor.prepareBatch].
     */
    suspend fun prepareScene(scene: Scene, durationMinutes: Int?): WizardBolusExecutor.PrepareResult {
        validateActivation(scene)?.let { return WizardBolusExecutor.PrepareResult.Error(it) }
        val effective = durationMinutes ?: scene.defaultDurationMinutes
        val bolusId = dateUtil.now()
        // Trim abandoned parks (user cancelled / app killed) so a stale prepare can't be committed minutes later.
        pendingScenes.evictParkedBefore(bolusId - PARK_TTL_MS)
        pendingScenes.park(bolusId, ParkedScene(scene, effective, bolusId))
        return WizardBolusExecutor.PrepareResult.Preview(
            insulin = 0.0, carbs = 0, bolusId = bolusId,
            lines = buildSceneLines(scene, effective), advisorApplies = false, advisorLines = emptyList()
        )
    }

    /**
     * Two-step COMMIT (master side): drain the parked scene matching [bolusId] EXACTLY once and activate it. A re-sent
     * commit finds the slot drained → [WizardBolusExecutor.ConfirmResult.NoPending] (no double-activate). An activation
     * failure rides back through [onError]. Mirrors [WizardBolusExecutor.confirm].
     */
    suspend fun commitScene(bolusId: Long, onError: (String) -> Unit): WizardBolusExecutor.ConfirmResult {
        val parked = pendingScenes.take(bolusId) ?: return WizardBolusExecutor.ConfirmResult.NoPending
        val result = activate(parked.scene, parked.durationMinutes)
        if (!result.success) onError(result.errorMessage ?: rh.gs(CoreUiStrings.scene_some_actions_failed))
        return WizardBolusExecutor.ConfirmResult.Delivered
    }

    /** The master-authored confirmation lines for a scene — name + duration + one line per action (authored ONCE here). */
    private fun buildSceneLines(scene: Scene, durationMinutes: Int): List<ConfirmationLine> = buildList {
        add(ConfirmationLine(ConfirmationRole.SCENE, scene.name))
        if (durationMinutes > 0)
            add(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(InterfacesStrings.confirmation_line, rh.gs(CoreUiStrings.duration), formatMinutesAsDuration(durationMinutes, rh))))
        scene.actions.forEach { add(ConfirmationLine(ConfirmationRole.NORMAL, sceneActionLine(it))) }
    }

    private fun sceneActionLine(action: SceneAction): String = when (action) {
        is SceneAction.TempTarget      -> rh.gs(CoreUiStrings.scene_action_tt, profileUtil.fromMgdlToStringWithUnits(action.targetMgdl))
        is SceneAction.ProfileSwitch   -> if (action.profileName.isNotEmpty())
            rh.gs(CoreUiStrings.scene_action_profile, action.profileName, action.percentage)
        else
            rh.gs(CoreUiStrings.scene_action_profile_pct_only, action.percentage)
        is SceneAction.SmbToggle       -> if (action.enabled) rh.gs(CoreUiStrings.scene_action_smb_on) else rh.gs(CoreUiStrings.scene_action_smb_off)
        is SceneAction.LoopModeChange  -> rh.gs(CoreUiStrings.scene_action_running_mode, translator.translate(action.mode))
        is SceneAction.CarePortalEvent -> rh.gs(CoreUiStrings.scene_action_careportal, translator.translate(action.type))
    }

    /**
     * Activate a scene: capture prior state, execute all actions, persist active state.
     * @param scene The scene to activate
     * @param durationMinutes Override duration in minutes (0 = indefinite)
     * @return Result of the execution
     */
    suspend fun activate(scene: Scene, durationMinutes: Int = scene.defaultDurationMinutes): SceneExecutionResult {

        // Defensive precondition gate — covers automation rules and races
        // where UI state lagged behind reality. UI normally disables the entry
        // point, but never trust the caller.
        validateActivation(scene)?.let { reason ->
            aapsLogger.warn(LTag.UI, "activate() blocked: $reason (scene='${scene.name}')")
            return SceneExecutionResult(success = false, errorMessage = reason)
        }

        // Wind down any currently active scene first.
        // - Not yet expired: full revert (TT/PS/RM cancel, SMB restore) so SceneA's effects don't
        //   leak past SceneB's start.
        // - Already expired: its expiry worker is either running right now (chained activation —
        //   we are inside that worker) or already finished, and onExpiry() has already reverted
        //   non-duration actions. Calling cancelUniqueWork on the running worker would cancel our
        //   own coroutine scope, so we just clear the state. REPLACE policy on the new
        //   scheduleExpiryWorker handles any leftover work.
        if (activeSceneManager.isActive()) {
            val previouslyExpired = activeSceneManager.isExpired()
            if (!previouslyExpired) {
                deactivate()
            } else {
                activeSceneManager.clearActive()
            }
        }

        val now = dateUtil.now()
        val durationMs = T.mins(durationMinutes.toLong()).msecs()

        // Capture pre-activation SMB flag (the only "truly prior" state) before making changes.
        val priorSmb = capturePriorSmb(scene)

        // Execute each action
        val actionResults = mutableListOf<SceneExecutionResult.ActionResult>()
        for (action in scene.actions) {
            val result = executeAction(action, durationMinutes, now)
            actionResults.add(result)
        }

        // Capture record IDs of what we just created — used for chip detection and to spot manual
        // overrides at revert time. NS ids fill in asynchronously as records get uploaded.
        val scopedRecords = ActiveSceneState.ScopedRecords(
            ttId = actionResults.firstOrNull { it.action is SceneAction.TempTarget }?.recordId,
            psId = actionResults.firstOrNull { it.action is SceneAction.ProfileSwitch }?.recordId,
            rmId = actionResults.firstOrNull { it.action is SceneAction.LoopModeChange }?.recordId,
            teId = actionResults.firstOrNull { it.action is SceneAction.CarePortalEvent }?.recordId
        )

        val activeState = ActiveSceneState(
            scene = scene,
            activatedAt = now,
            durationMs = durationMs,
            priorSmb = priorSmb,
            scopedRecords = scopedRecords
        )
        activeSceneManager.setActive(activeState)

        // Schedule expiry notification if duration-based
        if (durationMs > 0) sceneExpiryScheduler.schedule(scene.name, durationMs)

        // Log user entry
        uel.log(
            action = Action.SCENE_ACTIVATED,
            source = Sources.Scene,
            note = scene.name,
            listValues = listOf(
                ValueWithUnit.SimpleString(scene.name),
                ValueWithUnit.Minute(durationMinutes)
            )
        )

        val allSuccess = actionResults.all { it.success }
        return SceneExecutionResult(
            success = allSuccess,
            actionResults = actionResults,
            errorMessage = if (!allSuccess) rh.gs(CoreUiStrings.scene_some_actions_failed) else null
        )
    }

    /**
     * Deactivate the currently active scene: revert prior state.
     * @return Result of the deactivation
     */
    suspend fun deactivate(): SceneExecutionResult {
        val activeState = activeSceneManager.getActiveState()
            ?: return SceneExecutionResult(success = false, errorMessage = rh.gs(CoreUiStrings.scene_no_active))

        val now = dateUtil.now()
        val actionResults = mutableListOf<SceneExecutionResult.ActionResult>()

        // Revert each action type based on prior state
        for (action in activeState.scene.actions) {
            val result = revertAction(action, activeState, now)
            actionResults.add(result)
        }

        // Clear active state and cancel expiry worker
        activeSceneManager.clearActive()
        sceneExpiryScheduler.cancel()

        // Log user entry
        uel.log(
            action = Action.SCENE_DEACTIVATED,
            source = Sources.Scene,
            note = activeState.scene.name,
            listValues = listOf(ValueWithUnit.SimpleString(activeState.scene.name))
        )

        val allSuccess = actionResults.all { it.success }
        return SceneExecutionResult(
            success = allSuccess,
            actionResults = actionResults,
            errorMessage = if (!allSuccess) rh.gs(CoreUiStrings.scene_some_reverts_failed) else null
        )
    }

    /**
     * Called when scene duration expires. Reverts only non-duration actions (SMB toggle)
     * since duration-based actions (TT, Profile, LoopMode) already expired on their own.
     * Marks the scene as expired so the banner shows "Dismiss" instead of "End Scene".
     */
    suspend fun onExpiry() {
        val activeState = activeSceneManager.getActiveState()
        if (activeState == null) {
            return
        }

        val now = dateUtil.now()

        // Revert actions whose effect does NOT end on its own once the duration elapses:
        //  - SmbToggle: a preference with no duration model — must be restored explicitly.
        //  - ProfileSwitch: the timed ProfileSwitch record expires, but the EffectiveProfileSwitch it
        //    produced does NOT — getEffectiveProfileSwitchActiveAt() selects the latest EPS by timestamp
        //    and ignores originalEnd, so the base profile only resumes once a NEW base-profile EPS exists.
        //    revertAction() creates it now (cancelProfileSwitch + EventProfileChangeRequested), exactly as
        //    deactivate() does, instead of leaving it to the master's next KeepAliveWorker pass — that pass
        //    can be up to ~5 min late and is skipped entirely while the pump is disconnected, widening the
        //    window during which a reconnecting client mirrors no active profile ("no profile set").
        // TT / LoopMode / CarePortal records self-expire via their own timestamp+duration queries.
        for (action in activeState.scene.actions) {
            if (action is SceneAction.SmbToggle || action is SceneAction.ProfileSwitch) {
                revertAction(action, activeState, now)
            }
        }

        // Mark as expired (keep state for banner display) instead of clearing.
        // Lifecycle change rides the existing RunningConfigurationPublisher cycle to clients.
        activeSceneManager.markExpired()

        // Log
        uel.log(
            action = Action.SCENE_DEACTIVATED,
            source = Sources.Scene,
            note = activeState.scene.name,
            listValues = listOf(ValueWithUnit.SimpleString(activeState.scene.name))
        )
    }

    /**
     * Dismiss the expired scene banner. No revert — everything already handled by onExpiry.
     *
     * Also clears the notifications this scene-end produced so confirming the banner tidies them
     * away in one action: always the "Scene … ended" card ([NotificationId.SCENE_ENDED]), and — only
     * when the scene actually performed a ProfileSwitch — the single-instance "Basal profile in pump
     * updated" card ([NotificationId.PROFILE_SET_OK]) that its revert can raise. The profile card is
     * meant to be silenced (#4959); the scoped dismiss here is a no-op when suppression works and a
     * cleanup when it leaks. Reading the active state before [ActiveSceneManager.clearActive] is
     * required — it is gone afterwards.
     */
    fun dismiss() {
        val hadProfileSwitch = activeSceneManager.getActiveState()
            ?.scene?.actions?.any { it is SceneAction.ProfileSwitch } == true
        activeSceneManager.clearActive()
        notificationManager.dismiss(NotificationId.SCENE_ENDED)
        if (hadProfileSwitch) notificationManager.dismiss(NotificationId.PROFILE_SET_OK)
    }

    private fun capturePriorSmb(scene: Scene): Boolean? {
        // SMB is the only action without a duration model — capture its prior value so revert
        // can restore the user's preference. TT/PS/RM revert by shortening their own record;
        // the resolver picks up whatever was underneath, so no snapshot is needed for them.
        return scene.actions.firstOrNull { it is SceneAction.SmbToggle }?.let {
            preferences.get(BooleanKey.ApsUseSmb)
        }
    }

    /**
     * Runs a scene-driven ProfileSwitch DB mutation ([block]) with the [ProfileSwitchSilentGate] armed, and
     * GUARANTEES the one-shot gate is disarmed again on every path that produces no observeChanges(ProfileSwitch)
     * emission: the write reported "no row changed" ([wroteRow] returns false) or threw. Only a real row change
     * leaves the gate armed — to be consumed by the emission that change triggers. Without this, a no-op or
     * throwing scene write would leak silence onto the NEXT unrelated profile switch (issue #4959 follow-up).
     * [block] is inlined into the caller's coroutine, so it may suspend.
     */
    private inline fun <T> silencedProfileWrite(wroteRow: (T) -> Boolean, block: () -> T): T {
        profileSwitchSilentGate.markNextSilent()
        val result = try {
            block()
        } catch (e: Exception) {
            profileSwitchSilentGate.consumeSilent()
            throw e
        }
        if (!wroteRow(result)) profileSwitchSilentGate.consumeSilent()
        return result
    }

    private suspend fun executeAction(
        action: SceneAction,
        sceneDurationMinutes: Int,
        now: Long
    ): SceneExecutionResult.ActionResult {
        return try {
            when (action) {
                is SceneAction.TempTarget      -> {
                    val ttDuration = if (sceneDurationMinutes > 0) T.mins(sceneDurationMinutes.toLong()).msecs() else Long.MAX_VALUE
                    val tempTarget = TT(
                        timestamp = now,
                        duration = ttDuration,
                        reason = action.reason,
                        lowTarget = action.targetMgdl,
                        highTarget = action.targetMgdl
                    )
                    val result = persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        temporaryTarget = tempTarget,
                        action = Action.TT,
                        source = Sources.Scene,
                        note = null,
                        listValues = listOf(
                            ValueWithUnit.Mgdl(action.targetMgdl),
                            ValueWithUnit.Minute(sceneDurationMinutes)
                        )
                    )
                    SceneExecutionResult.ActionResult(action, success = true, recordId = result.inserted.firstOrNull()?.id)
                }

                is SceneAction.ProfileSwitch   -> {
                    val store = profileRepository.profile.value
                    // Use the BASE profile name as fallback — getProfileName() returns the display name
                    // including temp-% suffix (e.g. "Test (60%)"), which doesn't exist in the profile store.
                    val profileName = action.profileName.ifEmpty { profileFunction.getOriginalProfileName() }
                    // The switch has to record an insulin. A scene runs unattended, so with nothing in force there is
                    // nobody to ask — fail the action rather than stamp an arbitrary catalogue entry onto it.
                    val iCfg = profileFunction.getRunningOrRequestedICfg()
                    if (store != null && iCfg != null) {
                        // Scene-driven profile write: suppress the central "Basal profile in pump updated"
                        // notification for the pump write this insert triggers (issue #4959). createProfileSwitch
                        // wrote a PS row (and will emit) iff it returns non-null; silencedProfileWrite resets the
                        // gate on the null / throwing paths.
                        val ps = silencedProfileWrite(wroteRow = { it != null }) {
                            profileFunction.createProfileSwitch(
                                profileStore = store,
                                profileName = profileName,
                                durationInMinutes = sceneDurationMinutes,
                                percentage = action.percentage,
                                timeShiftInHours = action.timeShiftHours,
                                timestamp = now,
                                action = Action.PROFILE_SWITCH,
                                source = Sources.Scene,
                                note = null,
                                listValues = listOf(
                                    ValueWithUnit.SimpleString(action.profileName),
                                    ValueWithUnit.Percent(action.percentage),
                                    ValueWithUnit.Minute(sceneDurationMinutes)
                                ),
                                iCfg = iCfg
                            )
                        }
                        SceneExecutionResult.ActionResult(
                            action = action,
                            success = ps != null,
                            recordId = ps?.id,
                            errorMessage = if (ps == null) "createProfileSwitch returned null for '$profileName'" else null
                        )
                    } else if (store == null) {
                        SceneExecutionResult.ActionResult(action, success = false, errorMessage = rh.gs(CoreUiStrings.scene_no_profile_store))
                    } else {
                        SceneExecutionResult.ActionResult(action, success = false, errorMessage = rh.gs(CoreUiStrings.profile_switch_no_insulin))
                    }
                }

                is SceneAction.SmbToggle       -> {
                    preferences.put(BooleanKey.ApsUseSmb, action.enabled)
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.LoopModeChange  -> {
                    val result = persistenceLayer.insertOrUpdateRunningMode(
                        runningMode = RM(
                            timestamp = now,
                            mode = action.mode,
                            autoForced = false,
                            duration = T.mins(sceneDurationMinutes.toLong()).msecs()
                        ),
                        action = Action.RUNNING_MODE,
                        source = Sources.Scene,
                        listValues = listOf(ValueWithUnit.SimpleString(action.mode.name))
                    )
                    SceneExecutionResult.ActionResult(action, success = true, recordId = result.inserted.firstOrNull()?.id)
                }

                is SceneAction.CarePortalEvent -> {
                    val teDuration = if (sceneDurationMinutes > 0) T.mins(sceneDurationMinutes.toLong()).msecs() else Long.MAX_VALUE
                    val te = TE(
                        timestamp = now,
                        type = action.type,
                        duration = teDuration,
                        note = action.note,
                        enteredBy = "AAPS",
                        glucoseUnit = GlucoseUnit.MGDL
                    )
                    val result = persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = te,
                        action = Action.CAREPORTAL,
                        source = Sources.Scene,
                        note = action.note,
                        listValues = listOf(
                            ValueWithUnit.TEType(action.type),
                            ValueWithUnit.Minute(sceneDurationMinutes)
                        )
                    )
                    SceneExecutionResult.ActionResult(action, success = true, recordId = result.inserted.firstOrNull()?.id)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to execute scene action: $action", e)
            SceneExecutionResult.ActionResult(action, success = false, errorMessage = e.message)
        }
    }

    private suspend fun revertAction(
        action: SceneAction,
        state: ActiveSceneState,
        now: Long
    ): SceneExecutionResult.ActionResult {
        val scoped = state.scopedRecords
        return try {
            when (action) {
                is SceneAction.TempTarget      -> {
                    // Check if scene's TT is still active (not manually overridden)
                    val currentTt = persistenceLayer.getTemporaryTargetActiveAt(now)
                    if (scoped.ttId != null && currentTt?.id == scoped.ttId) {
                        persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                            timestamp = now,
                            action = Action.CANCEL_TT,
                            source = Sources.Scene,
                            note = null,
                            listValues = emptyList()
                        )
                    } else {
                        aapsLogger.info(LTag.UI, "Skipping TT revert — TT was changed during scene")
                    }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.ProfileSwitch   -> {
                    // Shorten the scene's PS so its window ends now; the resolver picks up
                    // any underlying temp/permanent PS automatically (mirrors TT/RM revert).
                    // Override-protection: if the active EPS no longer derives from the scene's
                    // PS, the user changed profile during the scene — leave their choice alone.
                    val currentEps = persistenceLayer.getEffectiveProfileSwitchActiveAt(now)
                    val profileStillFromScene = scoped.psId != null &&
                        currentEps?.originalPsId == scoped.psId

                    if (profileStillFromScene) {
                        // Scene revert is an internal/automatic write — suppress the central "Basal profile in
                        // pump updated" notification (issue #4959). Two triggers reach onProfileChanged for this
                        // revert: cancelProfileSwitch's observeChanges(PS) emission (silenced by the armed gate) AND
                        // the explicit silent=true event below (which guarantees the revert write even if cancel was
                        // a no-op). cancelProfileSwitch changed a row (and will emit) iff its result has updates;
                        // silencedProfileWrite resets the gate on the no-op / throwing paths.
                        silencedProfileWrite(wroteRow = { it.updated.isNotEmpty() }) {
                            persistenceLayer.cancelProfileSwitch(
                                id = scoped.psId!!,
                                timestamp = now,
                                action = Action.PROFILE_SWITCH,
                                source = Sources.Scene,
                                note = null,
                                listValues = emptyList()
                            )
                        }
                        rxBus.send(EventProfileChangeRequested(silent = true))
                    } else {
                        aapsLogger.info(LTag.UI, "Skipping profile revert — profile was changed during scene")
                    }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.SmbToggle       -> {
                    state.priorSmb?.let { preferences.put(BooleanKey.ApsUseSmb, it) }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.LoopModeChange  -> {
                    // Check if scene's running mode is still active (not manually overridden)
                    val currentRm = persistenceLayer.getRunningModeActiveAt(now)
                    if (scoped.rmId != null && currentRm.id == scoped.rmId) {
                        persistenceLayer.cancelRunningMode(
                            id = scoped.rmId!!,
                            timestamp = now,
                            action = Action.RUNNING_MODE,
                            source = Sources.Scene,
                            listValues = emptyList()
                        )
                    } else {
                        aapsLogger.info(LTag.UI, "Skipping loop mode revert — mode was changed during scene")
                    }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.CarePortalEvent -> {
                    // Cut the scene's TE so its window ends at scene end (mirrors TT/PS/RM revert).
                    // For temp scenes the duration was already finite; for indefinite scenes it
                    // was Long.MAX_VALUE and gets shortened here. Skips if id missing (legacy
                    // state where TE id wasn't captured).
                    //
                    // No override-protection check (unlike PS/RM): TE has no "currently active"
                    // concept (multiple TEs coexist; no getActiveTherapyEventAt query). The
                    // transaction's own guards (invalid / started-after / already-finished)
                    // catch invalidations. Edit-during-scene of the TE's duration is a known
                    // minor gap — deemed acceptable since CarePortal events are stamps, not
                    // typically edited mid-scene.
                    scoped.teId?.let { teId ->
                        persistenceLayer.cancelTherapyEvent(
                            id = teId,
                            timestamp = now,
                            action = Action.CAREPORTAL,
                            source = Sources.Scene,
                            note = null,
                            listValues = emptyList()
                        )
                    }
                    SceneExecutionResult.ActionResult(action, success = true)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to revert scene action: $action", e)
            SceneExecutionResult.ActionResult(action, success = false, errorMessage = e.message)
        }
    }

    companion object {

        // A parked (prepared-but-not-committed) scene older than this is discarded on the next prepare — an
        // abandoned confirmation can't be committed long after the fact. Comfortably covers the round-trip window.
        private const val PARK_TTL_MS = 2L * 60L * 1000L
    }
}
