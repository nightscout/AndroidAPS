package app.aaps.ui.compose.scenes

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.profileNames
import app.aaps.core.objects.extensions.toScenes
import app.aaps.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class SceneListViewModel @Inject constructor(
    private val sceneRepository: SceneStore,
    private val activeSceneManager: ActiveSceneSync,
    private val persistenceLayer: PersistenceLayer,
    private val profileRepository: ProfileRepository,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val sceneActions: SceneActions,
    private val sceneChainTargetResolver: SceneChainResolver,
    private val nsClient: NsClient
) : ViewModel() {

    /** All defined scenes. */
    val scenes: StateFlow<List<Scene>> = sceneRepository.scenesFlow
        .map { it.toScenes() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sceneRepository.getScenes())

    /** Currently active scene state */
    val activeSceneState: StateFlow<ActiveSceneState?> = activeSceneManager.activeSceneState

    /** Scene IDs that have validation issues (e.g. missing profile) */
    private val _invalidSceneIds = MutableStateFlow<Set<String>>(emptySet())
    val invalidSceneIds: StateFlow<Set<String>> = _invalidSceneIds.asStateFlow()

    // Bumped on each runtime-state event so [activationReasons] recomputes.
    private val activationTick = MutableStateFlow(0)

    /** "Master is reachable for scene operations" signal — see [NsClient.masterReachable]. */
    private val masterReachable: StateFlow<Boolean> = nsClient.masterReachable

    /**
     * Top-of-screen banner string when scene operations are globally locked, or null when
     * operations are allowed. Today the only global lock is AAPSCLIENT-with-WS-disconnected.
     */
    val masterOfflineBanner: StateFlow<String?> = masterReachable
        .map { reachable ->
            if (reachable) null
            else rh.gs(if (!nsClient.masterControlAllowed.value) R.string.scene_lock_banner_control_disabled else R.string.scene_lock_banner_master_offline)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Per-scene edit-lock reason. Edit/delete/toggle buttons are disabled when this is non-null
     * for that scene. Reasons stack with WS-disconnected taking precedence over scene-active —
     * the user sees the most actionable explanation first.
     */
    val editLockReasons: StateFlow<Map<String, String?>> =
        combine(scenes, activeSceneState, masterReachable) { sceneList, active, reachable ->
            val masterReason = if (!reachable) rh.gs(if (!nsClient.masterControlAllowed.value) R.string.scene_lock_reason_control_disabled else R.string.scene_lock_reason_master_offline) else null
            val activeReason = rh.gs(R.string.scene_lock_reason_scene_active)
            sceneList.associate { scene ->
                scene.id to (masterReason ?: if (scene.id == active?.scene?.id) activeReason else null)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Per-scene activation gate: sceneId → localized reason if the scene
     * cannot be activated right now, or null if it can. Recomputes when
     * scenes change or pump/loop/profile state events fire.
     * UI uses this to disable the play button and surface the reason.
     */
    val activationReasons: StateFlow<Map<String, String?>> =
        combine(scenes, activationTick, masterReachable) { sceneList, _, reachable ->
            sceneList.associate { scene ->
                scene.id to when {
                    !reachable -> rh.gs(if (!nsClient.masterControlAllowed.value) R.string.scene_lock_reason_control_disabled else R.string.scene_lock_reason_master_offline)
                    else       -> sceneActions.validateActivation(scene)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Re-validate whenever scenes change
        viewModelScope.launch {
            scenes.collect { sceneList ->
                _invalidSceneIds.value = validateScenes(sceneList)
            }
        }
        // Refresh activationReasons on relevant runtime-state events.
        rxBus.toFlow(EventPumpStatusChanged::class.java)
            .onEach { activationTick.update { it + 1 } }.launchIn(viewModelScope)
        rxBus.toFlow(EventLoopUpdateGui::class.java)
            .onEach { activationTick.update { it + 1 } }.launchIn(viewModelScope)
        rxBus.toFlow(EventInitializationChanged::class.java)
            .onEach { activationTick.update { it + 1 } }.launchIn(viewModelScope)
        rxBus.toFlow(EventRefreshOverview::class.java)
            .onEach { activationTick.update { it + 1 } }.launchIn(viewModelScope)
    }

    private fun validateScenes(sceneList: List<Scene>): Set<String> {
        val profileList = profileRepository.profileNames()
        val knownIds = sceneList.mapTo(mutableSetOf()) { it.id }
        val invalid = mutableSetOf<String>()
        for (scene in sceneList) {
            if (scene.actions.isEmpty()) {
                invalid.add(scene.id)
                continue
            }
            for (action in scene.actions) {
                if (action is SceneAction.ProfileSwitch && action.profileName.isNotEmpty()) {
                    if (action.profileName !in profileList) {
                        invalid.add(scene.id)
                        break
                    }
                }
            }
            val chain = scene.endAction as? SceneEndAction.ChainScene
            if (chain != null && chain.sceneId !in knownIds) {
                invalid.add(scene.id)
            }
        }
        return invalid
    }

    /** Format minutes as human-readable duration using DateUtil */
    fun formatMinutes(minutes: Int): String =
        if (minutes == 0) rh.gs(R.string.scene_duration_indefinite)
        else dateUtil.niceTimeScalar(minutes * 60_000L, rh)

    // --- Dialog state ---

    sealed class DialogState {
        data class ConfirmActivation(
            val scene: Scene,
            val actionSummaries: List<String>,
            val conflicts: List<String>,
            /** The master's parked-scene id (from prepareStart) — committed on confirm. */
            val bolusId: Long
        ) : DialogState()

        data class ConfirmDeactivation(
            val sceneName: String,
            val revertSummaries: List<String>,
            /** Non-null when the active scene chains to a runnable target — render 3-button dialog
             *  with "Skip to <chainTargetName>" alongside "End Scene". */
            val chainTargetId: String? = null,
            val chainTargetName: String? = null
        ) : DialogState()

        data class ValidationError(
            val message: String
        ) : DialogState()
    }

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState: StateFlow<DialogState?> = _dialogState.asStateFlow()

    // --- Activation flow ---

    /**
     * Prepare step: ask the MASTER to PREPARE the scene and stash its authored confirmation in the
     * dialog. The `bolusId` returned in [ActionProgress.Prepared] is the master's **consume-once
     * commit token** (parked-scene id) — [confirmActivation] commits exactly that token, so the
     * master can ignore any duplicate commit for an already-consumed id.
     */
    fun requestActivation(scene: Scene) {
        viewModelScope.launch {
            // Scene disabled: ignore — UI already disables the play button.
            if (!scene.isEnabled) return@launch

            // Fast local gate (instant feedback). The MASTER re-validates + AUTHORS the confirmation at prepare.
            sceneActions.validateActivation(scene)?.let { reason ->
                _dialogState.value = DialogState.ValidationError(reason)
                return@launch
            }

            // Client-side conflict warnings (extra UX); the action summary itself now comes from the MASTER's lines.
            val conflicts = detectConflicts(scene)

            when (val prepared = sceneActions.prepareStart(scene.id)) {
                is ActionProgress.Prepared -> _dialogState.value =
                    DialogState.ConfirmActivation(scene, prepared.lines.map { it.text }, conflicts, prepared.id)

                is ActionProgress.Rejected ->
                    _dialogState.value = DialogState.ValidationError(prepared.detail ?: rh.gs(prepared.reason.failTextResId()))

                else                       -> Unit // Unconfirmed → the round-trip's app-level modal already showed it
            }
        }
    }

    /**
     * Commit step: clear the dialog FIRST (so a fast double-tap can't enqueue two commits) then
     * commit the master's prepared scene via its consume-once `bolusId` token — the master activates
     * exactly once (master local / client round-trip) and discards any duplicate for that id.
     */
    fun confirmActivation() {
        val state = _dialogState.value as? DialogState.ConfirmActivation ?: return
        _dialogState.value = null
        // Confirm the MASTER's prepared scene: activate it exactly once (master local / client round-trip).
        viewModelScope.launch { sceneActions.commitStart(state.bolusId) }
    }

    /**
     * Prepare step for ending the active scene: builds the revert summary + chain-target dialog.
     * Deactivation carries no consume-once `bolusId` (unlike activation) — the master derives the
     * target purely from its own active scene, so [confirmDeactivation] just clears the dialog and
     * calls stop; idempotence comes from there being a single active scene to end.
     */
    fun requestDeactivation() {
        val activeState = activeSceneManager.getActiveState() ?: return
        viewModelScope.launch {
            val revertSummaries = buildRevertSummaries(activeState)
            // Same chain detection MainViewModel.requestSceneDeactivation uses. On master we want
            // the runtime-aware check (don't offer chain when loop suspended / pump down /
            // profile cleared); on AAPSClient we use the catalog-only check (local runtime
            // state doesn't reflect master) and rely on master's TOCTOU re-check at receipt.
            val chainTarget = if (config.AAPSCLIENT) sceneChainTargetResolver.resolveCatalogChainTarget(activeState.scene)
            else sceneChainTargetResolver.resolveRunnableChainTarget(activeState.scene)
            _dialogState.value = DialogState.ConfirmDeactivation(
                sceneName = activeState.scene.name,
                revertSummaries = revertSummaries,
                chainTargetId = chainTarget?.id,
                chainTargetName = chainTarget?.name
            )
        }
    }

    /**
     * Commit step: clear the dialog FIRST (so a fast double-tap can't enqueue two stops) then end the
     * active scene. No `bolusId` token is needed — the master ends whatever scene is currently active,
     * so a duplicate stop after the scene is gone is a no-op.
     */
    fun confirmDeactivation() {
        _dialogState.value = null
        viewModelScope.launch { sceneActions.stop(triggerChain = false) }
    }

    /** Secondary action on the 3-button dialog: end current scene and immediately fire the chain target.
     *  The master derives the chain target from the active scene's endAction, so no id crosses the wire. */
    fun confirmDeactivationAndChain() {
        val state = _dialogState.value as? DialogState.ConfirmDeactivation ?: return
        state.chainTargetId ?: return // only offered when a chain target exists
        _dialogState.value = null
        viewModelScope.launch { sceneActions.stop(triggerChain = true) }
    }

    fun dismissDialog() {
        _dialogState.value = null
    }

    fun deleteScene(sceneId: String) {
        sceneRepository.deleteScene(sceneId)
    }

    fun toggleEnabled(sceneId: String) {
        val scene = sceneRepository.getScene(sceneId) ?: return
        sceneRepository.saveScene(scene.copy(isEnabled = !scene.isEnabled))
    }

    // --- Summary builders ---

    private suspend fun detectConflicts(scene: Scene): List<String> {
        val conflicts = mutableListOf<String>()
        val now = dateUtil.now()

        // Active TT conflict
        if (scene.actions.any { it is SceneAction.TempTarget }) {
            val activeTt = persistenceLayer.getTemporaryTargetActiveAt(now)
            if (activeTt != null) {
                conflicts.add(rh.gs(R.string.scene_conflict_active_tt))
            }
        }

        // Active profile override conflict (temporary PS or percentage != 100)
        if (scene.actions.any { it is SceneAction.ProfileSwitch }) {
            val activePs = persistenceLayer.getProfileSwitchActiveAt(now)
            if (activePs != null && (activePs.duration > 0 || activePs.percentage != 100)) {
                conflicts.add(rh.gs(R.string.scene_conflict_active_profile))
            }
        }

        // Active running mode conflict (user-set: temp or non-default mode; auto-forced rows
        // like SUSPENDED_BY_PUMP are pump-imposed and not a user-meaningful "override").
        if (scene.actions.any { it is SceneAction.LoopModeChange }) {
            val activeRm = persistenceLayer.getRunningModeActiveAt(now)
            if (activeRm.id != 0L && !activeRm.autoForced &&
                (activeRm.duration > 0 || activeRm.mode != RM.DEFAULT_MODE)
            ) {
                conflicts.add(rh.gs(R.string.scene_conflict_active_running_mode))
            }
        }

        // Active scene conflict
        val activeState = activeSceneManager.getActiveState()
        if (activeState != null) {
            conflicts.add(rh.gs(R.string.scene_conflict_active_scene, activeState.scene.name))
        }

        return conflicts
    }

    private fun buildRevertSummaries(activeState: ActiveSceneState): List<String> {
        val summaries = mutableListOf<String>()
        var hasCarePortal = false

        for (action in activeState.scene.actions) {
            when (action) {
                is SceneAction.TempTarget      -> {
                    summaries.add(rh.gs(R.string.scene_revert_tt))
                }

                is SceneAction.ProfileSwitch   -> {
                    summaries.add(rh.gs(R.string.scene_revert_profile))
                }

                is SceneAction.SmbToggle       -> {
                    val wasEnabled = activeState.priorSmb ?: true
                    summaries.add(
                        if (wasEnabled) rh.gs(R.string.scene_revert_smb_on)
                        else rh.gs(R.string.scene_revert_smb_off)
                    )
                }

                is SceneAction.LoopModeChange  -> {
                    summaries.add(rh.gs(R.string.scene_revert_loop_mode))
                }

                is SceneAction.CarePortalEvent -> {
                    hasCarePortal = true
                }
            }
        }

        if (hasCarePortal) {
            summaries.add(rh.gs(R.string.scene_careportal_no_revert))
        }

        return summaries
    }
}
