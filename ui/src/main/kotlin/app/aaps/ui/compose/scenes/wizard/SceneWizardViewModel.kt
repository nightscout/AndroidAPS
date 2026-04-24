package app.aaps.ui.compose.scenes.wizard

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.toTTPresets
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.scenes.SceneRepository
import app.aaps.ui.compose.scenes.SceneTemplate
import app.aaps.ui.compose.scenes.toScenes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@Stable
class SceneWizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sceneRepository: SceneRepository,
    private val localProfileManager: LocalProfileManager,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val translator: Translator,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) : ViewModel() {

    companion object {

        const val STEP_TEMPLATE = 0
        const val STEP_INFO = 1
        const val STEP_PROFILE = 2
        const val STEP_TEMP_TARGET = 3
        const val STEP_SMB = 4
        const val STEP_LOOP_MODE = 5
        const val STEP_CAREPORTAL = 6
        const val STEP_DURATION = 7
        const val STEP_CHAIN = 8
        const val STEP_NAME_ICON = 9
        const val TOTAL_STEPS = 9 // steps 1-9 (step 0 is template picker, not counted in progress)
    }

    data class WizardState(
        val template: SceneTemplate? = null,
        val currentStep: Int = STEP_TEMPLATE,
        // Per-action toggles
        val profileEnabled: Boolean = false,
        val ttEnabled: Boolean = false,
        val smbEnabled: Boolean = false,
        val loopModeEnabled: Boolean = false,
        val carePortalEnabled: Boolean = false,
        // Action configs
        val profileAction: SceneAction.ProfileSwitch = SceneAction.ProfileSwitch(profileName = "", percentage = 100),
        val ttAction: SceneAction.TempTarget? = null,
        val smbAction: SceneAction.SmbToggle = SceneAction.SmbToggle(enabled = false),
        val loopModeAction: SceneAction.LoopModeChange = SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS),
        val carePortalAction: SceneAction.CarePortalEvent = SceneAction.CarePortalEvent(type = TE.Type.EXERCISE),
        // Metadata
        val durationMinutes: Int = 60,
        val chainTargetId: String? = null,
        val name: String = "",
        val icon: String = "star"
    )

    private val editingSceneId: String? = savedStateHandle.get<String>("sceneId")
    val isEditMode: Boolean = editingSceneId != null

    private val _state = MutableStateFlow(WizardState())
    val state: StateFlow<WizardState> = _state.asStateFlow()

    init {
        if (editingSceneId != null) {
            val scene = sceneRepository.getScene(editingSceneId)
            if (scene != null) loadScene(scene)
        }
    }

    private fun loadScene(scene: Scene) {
        val profileAction = scene.actions.filterIsInstance<SceneAction.ProfileSwitch>().firstOrNull()
        val ttAction = scene.actions.filterIsInstance<SceneAction.TempTarget>().firstOrNull()
        val smbAction = scene.actions.filterIsInstance<SceneAction.SmbToggle>().firstOrNull()
        val loopModeAction = scene.actions.filterIsInstance<SceneAction.LoopModeChange>().firstOrNull()
        val carePortalAction = scene.actions.filterIsInstance<SceneAction.CarePortalEvent>().firstOrNull()

        _state.value = WizardState(
            currentStep = STEP_PROFILE,
            profileEnabled = profileAction != null,
            ttEnabled = ttAction != null,
            smbEnabled = smbAction != null,
            loopModeEnabled = loopModeAction != null,
            carePortalEnabled = carePortalAction != null,
            profileAction = profileAction ?: SceneAction.ProfileSwitch(profileName = "", percentage = 100),
            ttAction = ttAction,
            smbAction = smbAction ?: SceneAction.SmbToggle(enabled = false),
            loopModeAction = loopModeAction ?: SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS),
            carePortalAction = carePortalAction ?: SceneAction.CarePortalEvent(type = TE.Type.EXERCISE),
            durationMinutes = scene.defaultDurationMinutes,
            chainTargetId = (scene.endAction as? SceneEndAction.ChainScene)?.sceneId
                ?.takeIf { id -> sceneRepository.getScene(id) != null },
            name = scene.name,
            icon = scene.icon
        )
    }

    /** Other existing scenes available as chain targets (all except the one being edited), reactive. */
    val availableChainTargets: StateFlow<List<Scene>> = sceneRepository.scenesFlow
        .map { raw -> raw.toScenes().filter { it.id != editingSceneId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = sceneRepository.getScenes().filter { it.id != editingSceneId }
        )

    val profileNames: List<String>
        get() = localProfileManager.profile?.getProfileList()?.map { it.toString() } ?: emptyList()

    val ttPresets: List<TTPreset>
        get() = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()

    fun formatBgWithUnits(mgdl: Double): String =
        "${profileUtil.fromMgdlToStringInUnits(mgdl)} ${profileUtil.units.asText}"

    fun translateEventType(type: TE.Type): String = translator.translate(type)

    fun formatMinutes(minutes: Int): String = dateUtil.niceTimeScalar(minutes * 60_000L, rh)

    fun selectTemplate(template: SceneTemplate) {
        val hasAction = { cls: Class<*> -> template.defaultActions.any { cls.isInstance(it) } }

        // Determine default TT action: use user's preset matching the template reason, or null (no pre-selection)
        val templateTt = template.defaultActions.filterIsInstance<SceneAction.TempTarget>().firstOrNull()
        val matchingPreset = templateTt?.let { tt -> ttPresets.firstOrNull { it.reason == tt.reason } }
        val ttAction = matchingPreset?.let { SceneAction.TempTarget(reason = it.reason, targetMgdl = it.targetValue) }

        val carePortalAction = template.defaultActions.filterIsInstance<SceneAction.CarePortalEvent>().firstOrNull()
            ?: SceneAction.CarePortalEvent(type = TE.Type.EXERCISE)

        val profileAction = template.defaultActions.filterIsInstance<SceneAction.ProfileSwitch>().firstOrNull()
            ?: SceneAction.ProfileSwitch(profileName = "", percentage = 100)

        val smbAction = template.defaultActions.filterIsInstance<SceneAction.SmbToggle>().firstOrNull()
            ?: SceneAction.SmbToggle(enabled = false)

        val loopModeAction = template.defaultActions.filterIsInstance<SceneAction.LoopModeChange>().firstOrNull()
            ?: SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS)

        val templateName = rh.gs(template.nameResId)

        _state.value = WizardState(
            template = template,
            currentStep = if (template == SceneTemplate.BLANK) STEP_PROFILE else STEP_INFO,
            profileEnabled = hasAction(SceneAction.ProfileSwitch::class.java),
            ttEnabled = hasAction(SceneAction.TempTarget::class.java),
            smbEnabled = hasAction(SceneAction.SmbToggle::class.java),
            loopModeEnabled = hasAction(SceneAction.LoopModeChange::class.java),
            carePortalEnabled = hasAction(SceneAction.CarePortalEvent::class.java),
            profileAction = profileAction,
            ttAction = ttAction,
            smbAction = smbAction,
            loopModeAction = loopModeAction,
            carePortalAction = carePortalAction,
            durationMinutes = template.defaultDurationMinutes,
            name = if (template == SceneTemplate.BLANK) "" else templateName,
            icon = template.icon
        )
    }

    fun next() {
        _state.update {
            val nextStep = it.currentStep + 1
            if (nextStep <= STEP_NAME_ICON) it.copy(currentStep = nextStep) else it
        }
    }

    fun back() {
        _state.update {
            val prevStep = it.currentStep - 1
            val minStep = if (isEditMode) STEP_PROFILE else STEP_TEMPLATE
            // Skip info step when going back from Blank template
            val target = if (!isEditMode && prevStep == STEP_INFO && it.template == SceneTemplate.BLANK) STEP_TEMPLATE else prevStep
            if (target >= minStep) it.copy(currentStep = target) else it
        }
    }

    fun setProfileEnabled(enabled: Boolean) {
        _state.update { it.copy(profileEnabled = enabled) }
    }

    fun setTtEnabled(enabled: Boolean) {
        _state.update { it.copy(ttEnabled = enabled) }
    }

    fun setSmbEnabled(enabled: Boolean) {
        _state.update { it.copy(smbEnabled = enabled) }
    }

    fun setLoopModeEnabled(enabled: Boolean) {
        _state.update { it.copy(loopModeEnabled = enabled) }
    }

    fun setCarePortalEnabled(enabled: Boolean) {
        _state.update { it.copy(carePortalEnabled = enabled) }
    }

    fun updateProfileAction(action: SceneAction) {
        if (action is SceneAction.ProfileSwitch) _state.update { it.copy(profileAction = action) }
    }

    fun updateTtAction(action: SceneAction) {
        if (action is SceneAction.TempTarget) _state.update { it.copy(ttAction = action) }
    }

    fun updateSmbAction(action: SceneAction) {
        if (action is SceneAction.SmbToggle) _state.update { it.copy(smbAction = action) }
    }

    fun updateLoopModeAction(action: SceneAction) {
        if (action is SceneAction.LoopModeChange) _state.update { it.copy(loopModeAction = action) }
    }

    fun updateCarePortalAction(action: SceneAction) {
        if (action is SceneAction.CarePortalEvent) _state.update { it.copy(carePortalAction = action) }
    }

    fun setDuration(minutes: Int) {
        _state.update { it.copy(durationMinutes = minutes) }
    }

    fun setChainTarget(targetId: String?) {
        _state.update { it.copy(chainTargetId = targetId) }
    }

    fun setName(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun setIcon(iconKey: String) {
        _state.update { it.copy(icon = iconKey) }
    }

    fun save(): Boolean {
        val s = _state.value
        if (s.name.isBlank()) return false

        val sceneId = editingSceneId ?: UUID.randomUUID().toString()
        // Defensive: self-chain would infinite-loop on zero-duration scenes.
        // availableChainTargets already filters out editingSceneId, so this is only reachable
        // if state is manipulated outside the wizard UI — keep as a backstop.
        if (s.chainTargetId == sceneId) return false

        val actions = buildList {
            if (s.profileEnabled) add(s.profileAction)
            if (s.ttEnabled && s.ttAction != null) add(s.ttAction)
            if (s.smbEnabled) add(s.smbAction)
            if (s.loopModeEnabled) add(s.loopModeAction)
            if (s.carePortalEnabled) add(s.carePortalAction)
        }

        val endAction = s.chainTargetId?.let { SceneEndAction.ChainScene(it) }
            ?: SceneEndAction.Notification

        val scene = Scene(
            id = sceneId,
            name = s.name,
            icon = s.icon,
            defaultDurationMinutes = s.durationMinutes,
            actions = actions,
            endAction = endAction,
            isDeletable = true
        )
        sceneRepository.saveScene(scene)
        return true
    }
}
