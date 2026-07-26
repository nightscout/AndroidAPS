package app.aaps.core.data.model

/**
 * Runtime state of the currently active scene.
 *
 * Two distinct flavors of state, kept apart now that the field used to mash them together:
 * - [priorSmb]: pre-activation SMB flag, captured explicitly because SMB has no duration
 *   model and revert needs the previous value. The only "truly prior" field.
 * - [scopedRecords]: records this scene **created**. Used for chip detection
 *   ("is this TT scene-managed?") and revert detection of manual overrides. TT/PS/RM
 *   revert itself works by shortening the scene-created record so the resolver naturally
 *   falls back to the underlying state — no prior value needed.
 */
data class ActiveSceneState(
    /** The scene that is currently active */
    val scene: Scene,
    /** Timestamp when the scene was activated (epoch millis) */
    val activatedAt: Long,
    /** Duration in milliseconds (0 = indefinite, manual end only) */
    val durationMs: Long,
    /**
     * Lifecycle phase. Owned by master, synced to client. Distinct from [isExpired]
     * which is a clock-based predicate (duration elapsed by wall time). Lifecycle
     * transitions to [SceneLifecycle.EXPIRED] only after master runs its expiry
     * side-effects (revert SMB, chain scheduling) — so a client seeing EXPIRED can
     * confidently render the "Close" affordance.
     */
    val lifecycle: SceneLifecycle = SceneLifecycle.ACTIVE,
    /** SMB enabled state before activation (null if scene didn't change SMB) */
    val priorSmb: Boolean? = null,
    /** Records created by this scene at activation */
    val scopedRecords: ScopedRecords = ScopedRecords()
) {

    /** Calculated end time (null if indefinite) */
    val endsAt: Long?
        get() = if (durationMs > 0) activatedAt + durationMs else null

    /** Whether the scene has expired based on current time */
    fun isExpired(now: Long): Boolean = endsAt?.let { now >= it } ?: false

    /** Remaining time in milliseconds (null if indefinite, 0 if expired) */
    fun remainingMs(now: Long): Long? = endsAt?.let { maxOf(0L, it - now) }

    /**
     * Records this scene created at activation, paired by their local Room ID and NS
     * identifier. The Room ID drives chip detection on the host that owns the DB record;
     * the NS id is the cross-device key so a client can look up its local Room ID for the
     * same logical record. Either field of a pair may be null transiently — Room ID is set
     * at activation, NS id fills in once the record is uploaded; on the receiving side it
     * inverts.
     */
    data class ScopedRecords(
        val ttId: Long? = null, val ttNsId: String? = null,
        val psId: Long? = null, val psNsId: String? = null,
        val rmId: Long? = null, val rmNsId: String? = null,
        val teId: Long? = null, val teNsId: String? = null
    )
}
