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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
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
    private val rh: ResourceHelper
) {

    /**
     * Activate a scene: capture prior state, execute all actions, persist active state.
     * @param scene The scene to activate
     * @param durationMinutes Override duration in minutes (0 = indefinite)
     * @return Result of the execution
     */
    suspend fun activate(scene: Scene, durationMinutes: Int = scene.defaultDurationMinutes): SceneExecutionResult {
        // Deactivate any currently active scene first (without reverting)
        if (activeSceneManager.isActive()) {
            cancelExpiryWorker()
            activeSceneManager.clearActive()
        }

        val now = dateUtil.now()
        val durationMs = T.mins(durationMinutes.toLong()).msecs()

        // Capture prior state before making changes
        val priorState = capturePriorState(scene)

        // Execute each action
        val actionResults = mutableListOf<SceneExecutionResult.ActionResult>()
        for (action in scene.actions) {
            val result = executeAction(action, durationMinutes, now)
            actionResults.add(result)
        }

        // Query record IDs of what we just created (for override detection at revert time)
        val updatedPriorState = priorState.copy(
            sceneTtId = if (scene.actions.any { it is SceneAction.TempTarget })
                persistenceLayer.getTemporaryTargetActiveAt(now)?.id else null,
            scenePsId = actionResults.firstOrNull { it.action is SceneAction.ProfileSwitch }?.psId,
            sceneRunningModeId = if (scene.actions.any { it is SceneAction.LoopModeChange })
                persistenceLayer.getRunningModeActiveAt(now).id.takeIf { it > 0 } else null
        )

        // Set active state (with record IDs for override detection)
        val activeState = ActiveSceneState(
            scene = scene,
            activatedAt = now,
            durationMs = durationMs,
            priorState = updatedPriorState
        )
        activeSceneManager.setActive(activeState)

        // Schedule expiry notification if duration-based
        if (durationMs > 0) {
            scheduleExpiryWorker(scene.name, durationMs)
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
        val activeState = activeSceneManager.getActiveState() ?: return

        val now = dateUtil.now()

        // Only revert actions that have no duration and persist until manually reverted
        for (action in activeState.scene.actions) {
            if (action is SceneAction.SmbToggle) {
                revertAction(action, activeState.priorState, now)
            }
        }

        // Mark as expired (keep state for banner display) instead of clearing
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

    private suspend fun capturePriorState(scene: Scene): ActiveSceneState.PriorState {
        var smbEnabled: Boolean? = null
        var profileName: String? = null
        var profilePercentage: Int? = null
        var profileTimeShiftHours: Int? = null
        var runningMode: RM.Mode? = null

        for (action in scene.actions) {
            when (action) {
                is SceneAction.SmbToggle      -> {
                    smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
                }

                is SceneAction.ProfileSwitch  -> {
                    val profile = profileFunction.getProfile()
                    profileName = profileFunction.getProfileName()
                    profilePercentage = profile?.percentage ?: 100
                    profileTimeShiftHours = profile?.timeshift ?: 0
                }

                is SceneAction.LoopModeChange -> {
                    // Capture current running mode — will be used for revert
                    // The actual mode is managed by LoopPlugin, we just store what to revert to
                    runningMode = null // Will use RESUME on revert which cancels any running mode
                }

                is SceneAction.TempTarget,
                is SceneAction.CarePortalEvent -> {
                    // No prior state to capture for these
                }
            }
        }

        return ActiveSceneState.PriorState(
            smbEnabled = smbEnabled,
            profileName = profileName,
            profilePercentage = profilePercentage,
            profileTimeShiftHours = profileTimeShiftHours,
            runningMode = runningMode
        )
    }

    private suspend fun executeAction(
        action: SceneAction,
        sceneDurationMinutes: Int,
        now: Long
    ): SceneExecutionResult.ActionResult {
        return try {
            when (action) {
                is SceneAction.TempTarget       -> {
                    val ttDuration = if (sceneDurationMinutes > 0) T.mins(sceneDurationMinutes.toLong()).msecs() else Long.MAX_VALUE
                    val tempTarget = TT(
                        timestamp = now,
                        duration = ttDuration,
                        reason = action.reason,
                        lowTarget = action.targetMgdl,
                        highTarget = action.targetMgdl
                    )
                    persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        temporaryTarget = tempTarget,
                        action = Action.TT,
                        source = Sources.Scene,
                        note = null,
                        listValues = listOf(
                            ValueWithUnit.Mgdl(action.targetMgdl),
                            ValueWithUnit.Minute(sceneDurationMinutes)
                        )
                    )
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.ProfileSwitch    -> {
                    val store = localProfileManager.profile
                    val profileName = action.profileName.ifEmpty { profileFunction.getProfileName() }
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
                        SceneExecutionResult.ActionResult(action, success = ps != null, psId = ps?.id)
                    } else {
                        SceneExecutionResult.ActionResult(action, success = false, errorMessage = rh.gs(app.aaps.core.ui.R.string.scene_no_profile_store))
                    }
                }

                is SceneAction.SmbToggle        -> {
                    preferences.put(BooleanKey.ApsUseSmb, action.enabled)
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.LoopModeChange   -> {
                    persistenceLayer.insertOrUpdateRunningMode(
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
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.CarePortalEvent  -> {
                    val te = TE(
                        timestamp = now,
                        type = action.type,
                        duration = T.mins(sceneDurationMinutes.toLong()).msecs(),
                        note = action.note,
                        enteredBy = "AAPS",
                        glucoseUnit = GlucoseUnit.MGDL
                    )
                    persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = te,
                        action = Action.CAREPORTAL,
                        source = Sources.Scene,
                        note = action.note,
                        listValues = listOf(
                            ValueWithUnit.TEType(action.type),
                            ValueWithUnit.Minute(sceneDurationMinutes)
                        )
                    )
                    SceneExecutionResult.ActionResult(action, success = true)
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
                is SceneAction.TempTarget       -> {
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

                is SceneAction.ProfileSwitch    -> {
                    // Check if scene's profile is still active (PS id tracking via EPS.originalPsId)
                    val currentEps = persistenceLayer.getEffectiveProfileSwitchActiveAt(now)
                    val profileStillFromScene = priorState.scenePsId != null &&
                        currentEps?.originalPsId == priorState.scenePsId

                    if (profileStillFromScene) {
                        val name = priorState.profileName
                        val store = localProfileManager.profile
                        if (name != null && store != null) {
                            profileFunction.createProfileSwitch(
                                profileStore = store,
                                profileName = name,
                                durationInMinutes = 0, // permanent (revert)
                                percentage = priorState.profilePercentage ?: 100,
                                timeShiftInHours = priorState.profileTimeShiftHours ?: 0,
                                timestamp = now,
                                action = Action.PROFILE_SWITCH,
                                source = Sources.Scene,
                                note = null,
                                listValues = listOf(ValueWithUnit.SimpleString(name)),
                                iCfg = insulin.iCfg
                            )
                        }
                    } else {
                        aapsLogger.info(LTag.UI, "Skipping profile revert — profile was changed during scene")
                    }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.SmbToggle        -> {
                    priorState.smbEnabled?.let { preferences.put(BooleanKey.ApsUseSmb, it) }
                    SceneExecutionResult.ActionResult(action, success = true)
                }

                is SceneAction.LoopModeChange   -> {
                    // Check if scene's running mode is still active (not manually overridden)
                    val currentRm = persistenceLayer.getRunningModeActiveAt(now)
                    if (priorState.sceneRunningModeId != null && currentRm.id == priorState.sceneRunningModeId) {
                        persistenceLayer.cancelCurrentRunningMode(
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

                is SceneAction.CarePortalEvent  -> {
                    // CarePortal events are informational — no revert needed
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
