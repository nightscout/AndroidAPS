package app.aaps.ui.compose.scenes

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
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
    private val localProfileManager: LocalProfileManager,
    private val preferences: Preferences,
    private val activeSceneManager: ActiveSceneManager,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus
) {

    /**
     * Activate a scene: capture prior state, execute all actions, persist active state.
     * @param scene The scene to activate
     * @param durationMinutes Override duration in minutes (0 = indefinite)
     * @return Result of the execution
     */
    suspend fun activate(scene: Scene, durationMinutes: Int = scene.defaultDurationMinutes): SceneExecutionResult {
        aapsLogger.info(LTag.UI, "XXXX activate() entry scene='${scene.name}' id=${scene.id} duration=${durationMinutes}min actions=${scene.actions.size}")
        // Wind down any currently active scene first.
        // - Not yet expired: full revert (TT/PS/RM cancel, SMB restore) so SceneA's effects don't
        //   leak past SceneB's start.
        // - Already expired: its expiry worker is either running right now (chained activation —
        //   we are inside that worker) or already finished, and onExpiry() has already reverted
        //   non-duration actions. Calling cancelUniqueWork on the running worker would cancel our
        //   own coroutine scope, so we just clear the state. REPLACE policy on the new
        //   scheduleExpiryWorker handles any leftover work.
        if (activeSceneManager.isActive()) {
            val previouslyExpired = activeSceneManager.expired.value
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

        // Capture prior state before making changes
        aapsLogger.info(LTag.UI, "XXXX activate() calling capturePriorState()")
        val priorState = try {
            capturePriorState(scene)
        } catch (e: Throwable) {
            aapsLogger.error(LTag.UI, "XXXX activate() capturePriorState FAILED", e)
            throw e
        }
        aapsLogger.info(LTag.UI, "XXXX activate() capturePriorState() returned: $priorState")

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

        // Record IDs of what we just created (pulled from insert results for override detection at revert time)
        aapsLogger.info(LTag.UI, "XXXX activate() computing updatedPriorState")
        val updatedPriorState = try {
            priorState.copy(
                sceneTtId = actionResults.firstOrNull { it.action is SceneAction.TempTarget }?.recordId,
                scenePsId = actionResults.firstOrNull { it.action is SceneAction.ProfileSwitch }?.recordId,
                sceneRunningModeId = actionResults.firstOrNull { it.action is SceneAction.LoopModeChange }?.recordId,
                sceneTherapyEventId = actionResults.firstOrNull { it.action is SceneAction.CarePortalEvent }?.recordId
            )
        } catch (e: Throwable) {
            aapsLogger.error(LTag.UI, "XXXX activate() updatedPriorState FAILED", e)
            throw e
        }
        aapsLogger.info(LTag.UI, "XXXX activate() updatedPriorState computed")

        // Set active state (with record IDs for override detection)
        val activeState = ActiveSceneState(
            scene = scene,
            activatedAt = now,
            durationMs = durationMs,
            priorState = updatedPriorState
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
            errorMessage = if (!allSuccess) rh.gs(app.aaps.core.ui.R.string.scene_some_actions_failed) else null
        )
    }

    /**
     * Deactivate the currently active scene: revert prior state.
     * @return Result of the deactivation
     */
    suspend fun deactivate(): SceneExecutionResult {
        val activeState = activeSceneManager.getActiveState()
            ?: return SceneExecutionResult(success = false, errorMessage = rh.gs(app.aaps.core.ui.R.string.scene_no_active))

        val now = dateUtil.now()
        val actionResults = mutableListOf<SceneExecutionResult.ActionResult>()

        // Revert each action type based on prior state
        for (action in activeState.scene.actions) {
            val result = revertAction(action, activeState.priorState, now)
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
            errorMessage = if (!allSuccess) rh.gs(app.aaps.core.ui.R.string.scene_some_reverts_failed) else null
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

        // Only revert actions that have no duration and persist until manually reverted
        for (action in activeState.scene.actions) {
            if (action is SceneAction.SmbToggle) {
                aapsLogger.info(LTag.UI, "XXXX onExpiry() reverting SmbToggle")
                revertAction(action, activeState.priorState, now)
            }
        }

        // Mark as expired (keep state for banner display) instead of clearing
        aapsLogger.info(LTag.UI, "XXXX onExpiry() calling setExpired()")
        activeSceneManager.setExpired()

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

    private fun capturePriorState(scene: Scene): ActiveSceneState.PriorState {
        // SMB is the only action without a duration model — capture its prior value so revert
        // can restore the user's preference. TT/PS/RM revert by shortening their own record;
        // the resolver picks up whatever was underneath, so no snapshot is needed for them.
        val smbEnabled = scene.actions.firstOrNull { it is SceneAction.SmbToggle }?.let {
            preferences.get(BooleanKey.ApsUseSmb)
        }
        return ActiveSceneState.PriorState(smbEnabled = smbEnabled)
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
                    val store = localProfileManager.profile
                    // Use the BASE profile name as fallback — getProfileName() returns the display name
                    // including temp-% suffix (e.g. "Test (60%)"), which doesn't exist in the profile store.
                    val profileName = action.profileName.ifEmpty { profileFunction.getOriginalProfileName() }
                    if (store != null) {
                        val ps = profileFunction.createProfileSwitch(
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
                        SceneExecutionResult.ActionResult(
                            action = action,
                            success = ps != null,
                            recordId = ps?.id,
                            errorMessage = if (ps == null) "createProfileSwitch returned null for '$profileName'" else null
                        )
                    } else {
                        SceneExecutionResult.ActionResult(action, success = false, errorMessage = rh.gs(app.aaps.core.ui.R.string.scene_no_profile_store))
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
        priorState: ActiveSceneState.PriorState,
        now: Long
    ): SceneExecutionResult.ActionResult {
        return try {
            when (action) {
                is SceneAction.TempTarget      -> {
                    // Check if scene's TT is still active (not manually overridden)
                    val currentTt = persistenceLayer.getTemporaryTargetActiveAt(now)
                    if (priorState.sceneTtId != null && currentTt?.id == priorState.sceneTtId) {
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
                    val profileStillFromScene = priorState.scenePsId != null &&
                        currentEps?.originalPsId == priorState.scenePsId

                    if (profileStillFromScene) {
                        persistenceLayer.cancelProfileSwitch(
                            id = priorState.scenePsId!!,
                            timestamp = now,
                            action = Action.PROFILE_SWITCH,
                            source = Sources.Scene,
                            note = null,
                            listValues = emptyList()
                        )
                        rxBus.send(EventProfileChangeRequested())
                    } else {
                        aapsLogger.info(LTag.UI, "Skipping profile revert — profile was changed during scene")
                    }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.SmbToggle       -> {
                    priorState.smbEnabled?.let { preferences.put(BooleanKey.ApsUseSmb, it) }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.LoopModeChange  -> {
                    // Check if scene's running mode is still active (not manually overridden)
                    val currentRm = persistenceLayer.getRunningModeActiveAt(now)
                    if (priorState.sceneRunningModeId != null && currentRm.id == priorState.sceneRunningModeId) {
                        persistenceLayer.cancelRunningMode(
                            id = priorState.sceneRunningModeId!!,
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
                    priorState.sceneTherapyEventId?.let { teId ->
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
            val workerClass = Class.forName("app.aaps.receivers.SceneExpiryWorker")

            @Suppress("UNCHECKED_CAST")
            val request = OneTimeWorkRequest.Builder(workerClass as Class<androidx.work.ListenableWorker>)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString("scene_name", sceneName)
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
    }
}
