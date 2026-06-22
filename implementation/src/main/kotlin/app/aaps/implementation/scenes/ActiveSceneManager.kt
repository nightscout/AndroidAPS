package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.ActiveSceneState.ScopedRecords
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.scenes.ActiveSceneSnapshot
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the currently active scene state.
 *
 * Persists to SharedPreferences via [StringNonKey.ActiveScene] and exposes a [StateFlow]
 * for reactive UI updates. Also implements [ActiveSceneSync] so the NS settings publisher
 * can read a wire-shaped snapshot of the running scene, and so the client can apply one.
 *
 * Cross-device chip parity: [ScopedRecords] carries each scoped record's local Room ID
 * AND its NS identifier. A background subscription to record-change flows fills in the
 * missing half whenever it can — on master, the NS id once the record is uploaded; on
 * client, the local Room id once the underlying record syncs. Either side may briefly
 * have only one half populated; chips that depend on the local Room id fade in once it
 * arrives.
 */
@Singleton
class ActiveSceneManager @Inject constructor(
    private val preferences: Preferences,
    private val sceneRepository: SceneRepository,
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger
) : ActiveSceneSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSceneState = MutableStateFlow<ActiveSceneState?>(null)

    /** Observable active scene state. Lifecycle (ACTIVE / EXPIRED) lives inside the state. */
    override val activeSceneState: StateFlow<ActiveSceneState?> = _activeSceneState.asStateFlow()

    init {
        _activeSceneState.value = loadActiveState()

        // Backfill the missing half of each scoped record id pair as the underlying
        // records change. Symmetric: on master we have Room ids and watch for NS ids
        // to land; on client we have NS ids and watch for matching Room ids to appear.
        scope.launch { persistenceLayer.observeChanges<TT>().collect { reconcileTt(it) } }
        scope.launch { persistenceLayer.observeChanges<PS>().collect { reconcilePs(it) } }
        scope.launch { persistenceLayer.observeChanges<RM>().collect { reconcileRm(it) } }
        scope.launch { persistenceLayer.observeChanges<TE>().collect { reconcileTe(it) } }
    }

    /** Set the active scene state (called by SceneExecutor on activation) */
    fun setActive(state: ActiveSceneState) {
        aapsLogger.info(LTag.UI, "XXXX ActiveSceneManager.setActive('${state.scene.name}')")
        _activeSceneState.value = state
        persistActiveState(state)
    }

    /** Mark scene as expired (non-duration actions reverted, banner stays for dismiss).
     *  Called by master only — clients receive the EXPIRED lifecycle via NS sync. */
    fun markExpired() {
        val current = _activeSceneState.value ?: return
        if (current.lifecycle == SceneLifecycle.EXPIRED) return
        aapsLogger.info(LTag.UI, "XXXX ActiveSceneManager.markExpired() — scene='${current.scene.name}'")
        val updated = current.copy(lifecycle = SceneLifecycle.EXPIRED)
        _activeSceneState.value = updated
        persistActiveState(updated)
    }

    /** Clear the active scene (called by SceneExecutor on deactivation or dismiss) */
    fun clearActive() {
        aapsLogger.info(LTag.UI, "XXXX ActiveSceneManager.clearActive() — was='${_activeSceneState.value?.scene?.name}'")
        _activeSceneState.value = null
        preferences.put(StringNonKey.ActiveScene, "")
    }

    /** Check if any scene is currently active */
    fun isActive(): Boolean = _activeSceneState.value != null

    /** True once [markExpired] has run for the current active scene.
     *  Stays true until [clearActive]. Used by [SceneExpiryWorker] to make
     *  retried runs idempotent — onExpiry's revert + chain-activation must
     *  not happen twice. */
    fun isExpired(): Boolean = _activeSceneState.value?.lifecycle == SceneLifecycle.EXPIRED

    /** Get the current active state */
    override fun getActiveState(): ActiveSceneState? = _activeSceneState.value

    /** Update the scoped record set (called after scene activation to store record IDs) */
    fun updateScopedRecords(scopedRecords: ScopedRecords) {
        val state = _activeSceneState.value ?: return
        if (state.scopedRecords == scopedRecords) return
        val updated = state.copy(scopedRecords = scopedRecords)
        _activeSceneState.value = updated
        persistActiveState(updated)
    }

    // --- ActiveSceneSync ---

    override fun activeSceneSnapshot(): ActiveSceneSnapshot? =
        _activeSceneState.value?.let {
            ActiveSceneSnapshot(
                sceneId = it.scene.id,
                activatedAt = it.activatedAt,
                durationMs = it.durationMs,
                lifecycle = it.lifecycle,
                ttNsId = it.scopedRecords.ttNsId,
                psNsId = it.scopedRecords.psNsId,
                rmNsId = it.scopedRecords.rmNsId,
                teNsId = it.scopedRecords.teNsId
            )
        }

    override fun applyActiveScene(snapshot: ActiveSceneSnapshot?) {
        if (snapshot == null) {
            if (_activeSceneState.value == null) return
            aapsLogger.info(LTag.UI, "ActiveSceneManager.applyActiveScene(null) — clearing")
            clearActive()
            return
        }
        val scene = sceneRepository.getScene(snapshot.sceneId)
        if (scene == null) {
            aapsLogger.info(LTag.UI, "ActiveSceneManager.applyActiveScene: unknown sceneId='${snapshot.sceneId}', clearing")
            if (_activeSceneState.value != null) clearActive()
            return
        }
        // Apply immediately with NS ids only; Room ids resolve asynchronously below.
        val initial = ActiveSceneState(
            scene = scene,
            activatedAt = snapshot.activatedAt,
            durationMs = snapshot.durationMs,
            lifecycle = snapshot.lifecycle,
            priorSmb = null,                 // master-only, never on the wire
            scopedRecords = ScopedRecords(
                ttNsId = snapshot.ttNsId,
                psNsId = snapshot.psNsId,
                rmNsId = snapshot.rmNsId,
                teNsId = snapshot.teNsId
            )
        )
        if (initial == _activeSceneState.value) return
        aapsLogger.info(LTag.UI, "ActiveSceneManager.applyActiveScene('${scene.name}' lifecycle=${snapshot.lifecycle})")
        _activeSceneState.value = initial
        persistActiveState(initial)
        scope.launch { resolveRoomIdsForCurrent() }
    }

    private suspend fun resolveRoomIdsForCurrent() {
        val state = _activeSceneState.value ?: return
        val sr = state.scopedRecords
        val ttId = sr.ttId ?: sr.ttNsId?.let { persistenceLayer.getTemporaryTargetByNSId(it)?.id }
        val psId = sr.psId ?: sr.psNsId?.let { persistenceLayer.getProfileSwitchByNSId(it)?.id }
        val rmId = sr.rmId ?: sr.rmNsId?.let { persistenceLayer.getRunningModeByNSId(it)?.id }
        val teId = sr.teId ?: sr.teNsId?.let { persistenceLayer.getTherapyEventByNSId(it)?.id }
        val resolved = sr.copy(ttId = ttId, psId = psId, rmId = rmId, teId = teId)
        if (resolved != sr) updateScopedRecords(resolved)
    }

    // --- Symmetric backfill: fill the missing half of each id pair on record change ---

    private fun reconcileTt(records: List<TT>) {
        val sr = _activeSceneState.value?.scopedRecords ?: return
        val (id, nsId) = pickPair(records, sr.ttId, sr.ttNsId) { it.id to it.ids.nightscoutId } ?: return
        updateScopedRecords(sr.copy(ttId = id, ttNsId = nsId))
    }

    private fun reconcilePs(records: List<PS>) {
        val sr = _activeSceneState.value?.scopedRecords ?: return
        val (id, nsId) = pickPair(records, sr.psId, sr.psNsId) { it.id to it.ids.nightscoutId } ?: return
        updateScopedRecords(sr.copy(psId = id, psNsId = nsId))
    }

    private fun reconcileRm(records: List<RM>) {
        val sr = _activeSceneState.value?.scopedRecords ?: return
        val (id, nsId) = pickPair(records, sr.rmId, sr.rmNsId) { it.id to it.ids.nightscoutId } ?: return
        updateScopedRecords(sr.copy(rmId = id, rmNsId = nsId))
    }

    private fun reconcileTe(records: List<TE>) {
        val sr = _activeSceneState.value?.scopedRecords ?: return
        val (id, nsId) = pickPair(records, sr.teId, sr.teNsId) { it.id to it.ids.nightscoutId } ?: return
        updateScopedRecords(sr.copy(teId = id, teNsId = nsId))
    }

    /**
     * Find a record matching the half of the pair we already know, return the completed
     * pair if one or both halves can be filled. Returns null when nothing changes:
     * already complete, nothing to match, no record matches, or matched record's NS id
     * is still null (master case waiting for upload).
     */
    private inline fun <T> pickPair(
        records: List<T>,
        knownId: Long?,
        knownNsId: String?,
        idsOf: (T) -> Pair<Long, String?>
    ): Pair<Long, String?>? {
        if (knownId != null && knownNsId != null) return null         // already complete
        if (knownId == null && knownNsId == null) return null         // nothing to match
        val match = records.firstOrNull { r ->
            val (id, nsId) = idsOf(r)
            (knownId != null && id == knownId) || (knownNsId != null && nsId == knownNsId)
        } ?: return null
        val (mId, mNsId) = idsOf(match)
        // Master case (we know Room id): matched record's NS id may still be null — wait.
        if (knownId != null && mNsId == null) return null
        return mId to (knownNsId ?: mNsId)
    }

    // --- Persistence ---

    private fun persistActiveState(state: ActiveSceneState) {
        val json = JSONObject().apply {
            put("sceneId", state.scene.id)
            put("activatedAt", state.activatedAt)
            put("durationMs", state.durationMs)
            put("lifecycle", state.lifecycle.name)
            state.priorSmb?.let { put("priorSmb", it) }
            put("scopedRecords", state.scopedRecords.toJson())
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
            ActiveSceneState(
                scene = scene,
                activatedAt = json.getLong("activatedAt"),
                durationMs = json.getLong("durationMs"),
                lifecycle = json.optStringOrNull("lifecycle")
                    ?.let { runCatching { SceneLifecycle.valueOf(it) }.getOrNull() }
                    ?: SceneLifecycle.ACTIVE,
                priorSmb = if (json.has("priorSmb")) json.getBoolean("priorSmb") else null,
                scopedRecords = json.optJSONObject("scopedRecords")?.toScopedRecords() ?: ScopedRecords()
            )
        } catch (_: Exception) {
            null
        }
    }
}

// --- ScopedRecords serialization ---

private fun ScopedRecords.toJson(): JSONObject = JSONObject().apply {
    ttId?.let { put("ttId", it) }
    ttNsId?.let { put("ttNsId", it) }
    psId?.let { put("psId", it) }
    psNsId?.let { put("psNsId", it) }
    rmId?.let { put("rmId", it) }
    rmNsId?.let { put("rmNsId", it) }
    teId?.let { put("teId", it) }
    teNsId?.let { put("teNsId", it) }
}

private fun JSONObject.toScopedRecords(): ScopedRecords =
    ScopedRecords(
        ttId = optLongOrNull("ttId"),
        ttNsId = optStringOrNull("ttNsId"),
        psId = optLongOrNull("psId"),
        psNsId = optStringOrNull("psNsId"),
        rmId = optLongOrNull("rmId"),
        rmNsId = optStringOrNull("rmNsId"),
        teId = optLongOrNull("teId"),
        teNsId = optStringOrNull("teNsId")
    )

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key)) getLong(key) else null

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
