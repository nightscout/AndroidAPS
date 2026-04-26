package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the currently active scene state.
 * Persists to SharedPreferences via StringNonKey.ActiveScene.
 * Exposes a StateFlow for reactive UI updates.
 */
@Singleton
class ActiveSceneManager @Inject constructor(
    private val preferences: Preferences,
    private val sceneRepository: SceneRepository,
    private val aapsLogger: AAPSLogger
) {

    private val _activeSceneState = MutableStateFlow<ActiveSceneState?>(null)
    private val _expired = MutableStateFlow(false)

    /** Observable active scene state */
    val activeSceneState: StateFlow<ActiveSceneState?> = _activeSceneState.asStateFlow()

    /** Whether the active scene has expired (duration-based actions already reverted) */
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    init {
        // Restore from preferences on startup
        _activeSceneState.value = loadActiveState()
    }

    /** Set the active scene state (called by SceneExecutor on activation) */
    fun setActive(state: ActiveSceneState) {
        aapsLogger.info(LTag.UI, "XXXX ActiveSceneManager.setActive('${state.scene.name}')")
        _activeSceneState.value = state
        _expired.value = false
        persistActiveState(state)
    }

    /** Mark scene as expired (non-duration actions reverted, banner stays for dismiss) */
    fun setExpired() {
        aapsLogger.info(LTag.UI, "XXXX ActiveSceneManager.setExpired() — scene='${_activeSceneState.value?.scene?.name}'")
        _expired.value = true
    }

    /** Clear the active scene (called by SceneExecutor on deactivation or dismiss) */
    fun clearActive() {
        aapsLogger.info(LTag.UI, "XXXX ActiveSceneManager.clearActive() — was='${_activeSceneState.value?.scene?.name}'")
        _activeSceneState.value = null
        _expired.value = false
        preferences.put(StringNonKey.ActiveScene, "")
    }

    /** Check if any scene is currently active */
    fun isActive(): Boolean = _activeSceneState.value != null

    /** True once [setExpired] has been called for the current active scene.
     *  Stays true until [clearActive]. Used by [SceneExpiryWorker] to make
     *  retried runs idempotent — onExpiry's revert + chain-activation must
     *  not happen twice. */
    fun isExpired(): Boolean = _expired.value

    /** Get the current active state */
    fun getActiveState(): ActiveSceneState? = _activeSceneState.value

    /** Update the prior state (called after scene activation to store record IDs) */
    fun updatePriorState(priorState: ActiveSceneState.PriorState) {
        val state = _activeSceneState.value ?: return
        val updated = state.copy(priorState = priorState)
        _activeSceneState.value = updated
        persistActiveState(updated)
    }

    private fun persistActiveState(state: ActiveSceneState) {
        val json = JSONObject().apply {
            put("sceneId", state.scene.id)
            put("activatedAt", state.activatedAt)
            put("durationMs", state.durationMs)
            put("priorState", state.priorState.toJson())
        }
        preferences.put(StringNonKey.ActiveScene, json.toString())
    }

    private fun loadActiveState(): ActiveSceneState? {
        val raw = preferences.get(StringNonKey.ActiveScene)
        if (raw.isEmpty()) return null
        return try {
            val json = JSONObject(raw)
            val sceneId = json.getString("sceneId")
            val scene = sceneRepository.getScene(sceneId) ?: return null
            val priorJson = json.getJSONObject("priorState")
            ActiveSceneState(
                scene = scene,
                activatedAt = json.getLong("activatedAt"),
                durationMs = json.getLong("durationMs"),
                priorState = priorJson.toPriorState()
            )
        } catch (_: Exception) {
            null
        }
    }
}

// --- PriorState serialization ---

private fun ActiveSceneState.PriorState.toJson(): JSONObject = JSONObject().apply {
    smbEnabled?.let { put("smbEnabled", it) }
    profileName?.let { put("profileName", it) }
    profilePercentage?.let { put("profilePercentage", it) }
    profileTimeShiftHours?.let { put("profileTimeShiftHours", it) }
    runningMode?.let { put("runningMode", it.name) }
    sceneTtId?.let { put("sceneTtId", it) }
    sceneRunningModeId?.let { put("sceneRunningModeId", it) }
}

private fun JSONObject.toPriorState(): ActiveSceneState.PriorState = ActiveSceneState.PriorState(
    smbEnabled = if (has("smbEnabled")) getBoolean("smbEnabled") else null,
    profileName = if (has("profileName")) getString("profileName") else null,
    profilePercentage = if (has("profilePercentage")) getInt("profilePercentage") else null,
    profileTimeShiftHours = if (has("profileTimeShiftHours")) getInt("profileTimeShiftHours") else null,
    runningMode = if (has("runningMode")) {
        try {
            RM.Mode.valueOf(getString("runningMode"))
        } catch (_: Exception) {
            null
        }
    } else null,
    sceneTtId = if (has("sceneTtId")) getLong("sceneTtId") else null,
    sceneRunningModeId = if (has("sceneRunningModeId")) getLong("sceneRunningModeId") else null
)
