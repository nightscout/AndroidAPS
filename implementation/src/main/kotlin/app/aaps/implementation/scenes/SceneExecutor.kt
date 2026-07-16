package app.aaps.implementation.scenes

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
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
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.profileNames
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.formatMinutesAsDuration

/**
 * Executes scene activation and deactivation.
 * Captures prior state before activation for revert on deactivation.
 */
@Singleton
class SceneExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val insulin: Insulin,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val preferences: Preferences,
    private val activeSceneManager: ActiveSceneManager,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileUtil: ProfileUtil,
    private val translator: Translator,
    private val profileSwitchSilentGate: ProfileSwitchSilentGate
) {

    /** A parked scene activation awaiting [commitScene] — the two-step master-authoritative path. */
    private data class ParkedScene(val scene: Scene, val durationMinutes: Int, val parkedAt: Long)

    // Consume-once parked scenes keyed by bolusId (its OWN id-space, separate from the bolus executor's `pending`,
    // so a scene commit and a bolus commit can never drain each other). remove() is atomic → no double-activate.
    private val pendingScenes = ConcurrentHashMap<Long, ParkedScene>()

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
            return rh.gs(CoreUiR.string.pump_disconnected)
        if (!activePlugin.activePump.isInitialized() || profileFunction.getProfile() == null)
            return rh.gs(CoreUiR.string.pump_not_initialized_profile_not_set)
        if (scene.actions.isEmpty())
            return rh.gs(CoreUiR.string.scene_no_actions)
        val profileList = profileRepository.profileNames()
        for (action in scene.actions) {
            if (action is SceneAction.ProfileSwitch && action.profileName.isNotEmpty() &&
                action.profileName !in profileList
            ) return rh.gs(CoreUiR.string.scene_profile_not_found, action.profileName)
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
        pendingScenes.entries.removeAll { bolusId - it.value.parkedAt > PARK_TTL_MS }
        pendingScenes[bolusId] = ParkedScene(scene, effective, bolusId)
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
        val parked = pendingScenes.remove(bolusId) ?: return WizardBolusExecutor.ConfirmResult.NoPending
        val result = activate(parked.scene, parked.durationMinutes)
        if (!result.success) onError(result.errorMessage ?: rh.gs(CoreUiR.string.scene_some_actions_failed))
        return WizardBolusExecutor.ConfirmResult.Delivered
    }

    /** The master-authored confirmation lines for a scene — name + duration + one line per action (authored ONCE here). */
    private fun buildSceneLines(scene: Scene, durationMinutes: Int): List<ConfirmationLine> = buildList {
        add(ConfirmationLine(ConfirmationRole.SCENE, scene.name))
        if (durationMinutes > 0)
            add(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(CoreUiR.string.confirmation_line, rh.gs(CoreUiR.string.duration), formatMinutesAsDuration(durationMinutes, rh))))
        scene.actions.forEach { add(ConfirmationLine(ConfirmationRole.NORMAL, sceneActionLine(it))) }
    }

    private fun sceneActionLine(action: SceneAction): String = when (action) {
        is SceneAction.TempTarget      -> rh.gs(CoreUiR.string.scene_action_tt, profileUtil.fromMgdlToStringWithUnits(action.targetMgdl))
        is SceneAction.ProfileSwitch   -> if (action.profileName.isNotEmpty())
            rh.gs(CoreUiR.string.scene_action_profile, action.profileName, action.percentage)
        else
            rh.gs(CoreUiR.string.scene_action_profile_pct_only, action.percentage)
        is SceneAction.SmbToggle       -> if (action.enabled) rh.gs(CoreUiR.string.scene_action_smb_on) else rh.gs(CoreUiR.string.scene_action_smb_off)
        is SceneAction.LoopModeChange  -> rh.gs(CoreUiR.string.scene_action_running_mode, translator.translate(action.mode))
        is SceneAction.CarePortalEvent -> rh.gs(CoreUiR.string.scene_action_careportal, translator.translate(action.type))
    }

    /**
     * Activate a scene: capture prior state, execute all actions, persist active state.
     * @param scene The scene to activate
     * @param durationMinutes Override duration in minutes (0 = indefinite)
     * @return Result of the execution
     */
    suspend fun activate(scene: Scene, durationMinutes: Int = scene.defaultDurationMinutes): SceneExecutionResult {
        aapsLogger.info(LTag.UI, "XXXX activate() entry scene='${scene.name}' id=${scene.id} duration=${durationMinutes}min actions=${scene.actions.size}")

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
            aapsLogger.info(LTag.UI, "XXXX activate() — winding down previous active scene '${activeSceneManager.getActiveState()?.scene?.name}' expired=$previouslyExpired")
            if (!previouslyExpired) {
                deactivate()
            } else {
                activeSceneManager.clearActive()
            }
        } else {
            aapsLogger.info(LTag.UI, "XXXX activate() — no previous active scene")
        }

        val now = dateUtil.now()
        val durationMs = T.mins(durationMinutes.toLong()).msecs()
        aapsLogger.info(LTag.UI, "XXXX activate() now=$now durationMs=$durationMs")

        // Capture pre-activation SMB flag (the only "truly prior" state) before making changes.
        aapsLogger.info(LTag.UI, "XXXX activate() calling capturePriorSmb()")
        val priorSmb = try {
            capturePriorSmb(scene)
        } catch (e: Throwable) {
            aapsLogger.error(LTag.UI, "XXXX activate() capturePriorSmb FAILED", e)
            throw e
        }
        aapsLogger.info(LTag.UI, "XXXX activate() capturePriorSmb() returned: $priorSmb")

        // Execute each action
        val actionResults = mutableListOf<SceneExecutionResult.ActionResult>()
        for ((idx, action) in scene.actions.withIndex()) {
            aapsLogger.info(LTag.UI, "XXXX activate() executing action $idx/${scene.actions.size}: ${action::class.simpleName}")
            val result = try {
                executeAction(action, durationMinutes, now)
            } catch (e: Throwable) {
                aapsLogger.error(LTag.UI, "XXXX activate() executeAction #$idx FAILED", e)
                throw e
            }
            aapsLogger.info(LTag.UI, "XXXX activate() action $idx result: success=${result.success} err=${result.errorMessage}")
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
        aapsLogger.info(LTag.UI, "XXXX activate() setting active state for '${scene.name}'")
        activeSceneManager.setActive(activeState)

        // Schedule expiry notification if duration-based
        if (durationMs > 0) {
            aapsLogger.info(LTag.UI, "XXXX activate() scheduling expiry worker in ${durationMs}ms")
            scheduleExpiryWorker(scene.name, durationMs)
        } else {
            aapsLogger.info(LTag.UI, "XXXX activate() durationMs==0, no expiry worker scheduled (indefinite)")
        }

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
            errorMessage = if (!allSuccess) rh.gs(CoreUiR.string.scene_some_actions_failed) else null
        )
    }

    /**
     * Deactivate the currently active scene: revert prior state.
     * @return Result of the deactivation
     */
    suspend fun deactivate(): SceneExecutionResult {
        val activeState = activeSceneManager.getActiveState()
            ?: return SceneExecutionResult(success = false, errorMessage = rh.gs(CoreUiR.string.scene_no_active))

        val now = dateUtil.now()
        val actionResults = mutableListOf<SceneExecutionResult.ActionResult>()

        // Revert each action type based on prior state
        for (action in activeState.scene.actions) {
            val result = revertAction(action, activeState, now)
            actionResults.add(result)
        }

        // Clear active state and cancel expiry worker
        activeSceneManager.clearActive()
        cancelExpiryWorker()

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
            errorMessage = if (!allSuccess) rh.gs(CoreUiR.string.scene_some_reverts_failed) else null
        )
    }

    /**
     * Called when scene duration expires. Reverts only non-duration actions (SMB toggle)
     * since duration-based actions (TT, Profile, LoopMode) already expired on their own.
     * Marks the scene as expired so the banner shows "Dismiss" instead of "End Scene".
     */
    suspend fun onExpiry() {
        aapsLogger.info(LTag.UI, "XXXX onExpiry() entry")
        val activeState = activeSceneManager.getActiveState()
        if (activeState == null) {
            aapsLogger.info(LTag.UI, "XXXX onExpiry() — no active state, returning")
            return
        }
        aapsLogger.info(LTag.UI, "XXXX onExpiry() scene='${activeState.scene.name}' endAction=${activeState.scene.endAction}")

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
                aapsLogger.info(LTag.UI, "XXXX onExpiry() reverting ${action::class.simpleName}")
                revertAction(action, activeState, now)
            }
        }

        // Mark as expired (keep state for banner display) instead of clearing.
        // Lifecycle change rides the existing RunningConfigurationPublisher cycle to clients.
        aapsLogger.info(LTag.UI, "XXXX onExpiry() calling markExpired()")
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
     */
    fun dismiss() {
        activeSceneManager.clearActive()
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
                    if (store != null) {
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
                                iCfg = insulin.iCfg
                            )
                        }
                        SceneExecutionResult.ActionResult(
                            action = action,
                            success = ps != null,
                            recordId = ps?.id,
                            errorMessage = if (ps == null) "createProfileSwitch returned null for '$profileName'" else null
                        )
                    } else {
                        SceneExecutionResult.ActionResult(action, success = false, errorMessage = rh.gs(CoreUiR.string.scene_no_profile_store))
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

    private fun scheduleExpiryWorker(sceneName: String, delayMs: Long) {
        try {
            // Direct reference now that the worker lives in this module (was Class.forName when this
            // engine was in :ui and couldn't see the :app worker).
            val request = OneTimeWorkRequest.Builder(SceneExpiryWorker::class.java)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(SceneExpiryWorker.KEY_SCENE_NAME, sceneName)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_SCENE_EXPIRY, ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to schedule scene expiry worker", e)
        }
    }

    private fun cancelExpiryWorker() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_SCENE_EXPIRY)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to cancel scene expiry worker", e)
        }
    }

    companion object {

        private const val WORK_NAME_SCENE_EXPIRY = "SceneExpiry"

        // A parked (prepared-but-not-committed) scene older than this is discarded on the next prepare — an
        // abandoned confirmation can't be committed long after the fact. Comfortably covers the round-trip window.
        private const val PARK_TTL_MS = 2L * 60L * 1000L
    }
}
