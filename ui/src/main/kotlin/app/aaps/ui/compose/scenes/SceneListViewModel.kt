package app.aaps.ui.compose.scenes

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class SceneListViewModel @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val activeSceneManager: ActiveSceneManager,
    private val sceneExecutor: SceneExecutor,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val localProfileManager: LocalProfileManager,
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val translator: Translator
) : ViewModel() {

    /** All defined scenes */
    val scenes: StateFlow<List<Scene>> = sceneRepository.scenesFlow
        .map { it.toScenes() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sceneRepository.getScenes())

    /** Currently active scene state */
    val activeSceneState: StateFlow<ActiveSceneState?> = activeSceneManager.activeSceneState

    /** Scene IDs that have validation issues (e.g. missing profile) */
    private val _invalidSceneIds = MutableStateFlow<Set<String>>(emptySet())
    val invalidSceneIds: StateFlow<Set<String>> = _invalidSceneIds.asStateFlow()

    init {
        // Re-validate whenever scenes change
        viewModelScope.launch {
            scenes.collect { sceneList ->
                _invalidSceneIds.value = validateScenes(sceneList)
            }
        }
    }

    private fun validateScenes(sceneList: List<Scene>): Set<String> {
        val profileList = localProfileManager.profile?.getProfileList()?.map { it.toString() } ?: emptyList()
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
            val conflicts: List<String>
        ) : DialogState()

        data class ConfirmDeactivation(
            val sceneName: String,
            val revertSummaries: List<String>
        ) : DialogState()

        data class ValidationError(
            val message: String
        ) : DialogState()
    }

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState: StateFlow<DialogState?> = _dialogState.asStateFlow()

    // --- Activation flow ---

    fun requestActivation(scene: Scene) {
        viewModelScope.launch {
            // Validation: scene disabled
            if (!scene.isEnabled) return@launch

            // Validation: pump disconnected / loop suspended
            if (loop.runningMode().isSuspended()) {
                _dialogState.value = DialogState.ValidationError(rh.gs(R.string.pump_disconnected))
                return@launch
            }

            // Validation: pump not ready or no profile
            if (!activePlugin.activePump.isInitialized() || profileFunction.getProfile() == null) {
                _dialogState.value = DialogState.ValidationError(rh.gs(R.string.pump_not_initialized_profile_not_set))
                return@launch
            }

            // Validation: no actions
            if (scene.actions.isEmpty()) {
                _dialogState.value = DialogState.ValidationError(rh.gs(R.string.scene_no_actions))
                return@launch
            }

            // Validation: profile exists
            for (action in scene.actions) {
                if (action is SceneAction.ProfileSwitch && action.profileName.isNotEmpty()) {
                    val profileList = localProfileManager.profile?.getProfileList()?.map { it.toString() } ?: emptyList()
                    if (action.profileName !in profileList) {
                        _dialogState.value = DialogState.ValidationError(
                            rh.gs(R.string.scene_profile_not_found, action.profileName)
                        )
                        return@launch
                    }
                }
            }

            // Build action summaries
            val summaries = scene.actions.map { buildActionSummary(it) }

            // Detect conflicts
            val conflicts = detectConflicts(scene)

            _dialogState.value = DialogState.ConfirmActivation(scene, summaries, conflicts)
        }
    }

    fun confirmActivation() {
        val state = _dialogState.value as? DialogState.ConfirmActivation ?: return
        _dialogState.value = null
        viewModelScope.launch {
            sceneExecutor.activate(state.scene)
        }
    }

    fun requestDeactivation() {
        val activeState = activeSceneManager.getActiveState() ?: return
        viewModelScope.launch {
            val revertSummaries = buildRevertSummaries(activeState)
            _dialogState.value = DialogState.ConfirmDeactivation(
                sceneName = activeState.scene.name,
                revertSummaries = revertSummaries
            )
        }
    }

    fun confirmDeactivation() {
        _dialogState.value = null
        viewModelScope.launch {
            sceneExecutor.deactivate()
        }
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

    private fun buildActionSummary(action: SceneAction): String {
        return when (action) {
            is SceneAction.TempTarget      -> {
                val targetStr = "${profileUtil.fromMgdlToStringInUnits(action.targetMgdl)} ${profileUtil.units.asText}"
                rh.gs(R.string.scene_action_tt, targetStr)
            }

            is SceneAction.ProfileSwitch   -> {
                rh.gs(R.string.scene_action_profile, action.profileName, action.percentage)
            }

            is SceneAction.SmbToggle       -> {
                if (action.enabled) rh.gs(R.string.scene_action_smb_on)
                else rh.gs(R.string.scene_action_smb_off)
            }

            is SceneAction.LoopModeChange  -> {
                rh.gs(R.string.scene_action_running_mode, translator.translate(action.mode))
            }

            is SceneAction.CarePortalEvent -> {
                rh.gs(R.string.scene_action_careportal, translator.translate(action.type))
            }
        }
    }

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

    private suspend fun buildRevertSummaries(activeState: ActiveSceneState): List<String> {
        val summaries = mutableListOf<String>()
        val prior = activeState.priorState
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
                    val wasEnabled = prior.smbEnabled ?: true
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
